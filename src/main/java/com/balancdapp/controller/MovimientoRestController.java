package com.balancdapp.controller;

import com.balancdapp.model.Categoria;
import com.balancdapp.model.Cuenta;
import com.balancdapp.model.Subcategoria;
import com.balancdapp.model.TipoCategoria;
import com.balancdapp.model.User;
import com.balancdapp.repository.CategoriaRepository;
import com.balancdapp.repository.SubcategoriaRepository;
import com.balancdapp.service.CategoriaService;
import com.balancdapp.service.EncryptedCuentaService;
import com.balancdapp.service.EncryptedMovimientoService;
import com.balancdapp.service.JwtService;
import com.balancdapp.service.UserService;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * API pensada también para consumo externo (p. ej. un atajo de la app Atajos de iPhone):
 * autenticación por JWT (POST /api/login), y endpoints de solo-lectura para poblar selectores
 * ("Elegir de lista" en Atajos) antes de crear el movimiento.
 */
@RestController
@RequestMapping("/api")
public class MovimientoRestController {
    @Autowired
    private EncryptedMovimientoService encryptedMovimientoService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserService userService;

    @Autowired
    private EncryptedCuentaService encryptedCuentaService;

    @Autowired
    private CategoriaService categoriaService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private SubcategoriaRepository subcategoriaRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

    private User authenticate(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        Claims claims = jwtService.validateToken(authHeader.substring(7));
        if (claims == null) {
            return null;
        }
        Long userId = ((Number) claims.get("id")).longValue();
        User user = userService.getUserById(userId).orElse(null);
        // Se relee el usuario fresco de BD en cada llamada (no solo del token), así que un
        // baneo desde el panel de administración corta el acceso por API de inmediato, sin
        // esperar a que el JWT expire.
        return (user != null && user.isBaneado()) ? null : user;
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token no proporcionado o inválido"));
    }

    /**
     * Convierte "cantidad" a número tolerando el formato que mandan automatizaciones externas
     * (p. ej. la automatización de pago con tarjeta del iPhone manda algo como "2 €" o "12,50€").
     * Quita símbolo de moneda, espacios (normales y de no separación) y letras, y normaliza el
     * separador decimal: "12,50" → 12.5, "1.234,56" → 1234.56, "1,234.56" → 1234.56, "12.5" → 12.5.
     */
    private double parseCantidad(Object raw) {
        String limpio = raw.toString().trim().replaceAll("[^0-9,.\\-]", "");
        if (limpio.isEmpty()) {
            throw new NumberFormatException("\"cantidad\" no contiene ningún número");
        }
        int lastComma = limpio.lastIndexOf(',');
        int lastDot = limpio.lastIndexOf('.');
        if (lastComma != -1 && lastDot != -1) {
            // El símbolo que aparece más a la derecha es el separador decimal; el otro se descarta (miles).
            if (lastComma > lastDot) {
                limpio = limpio.replace(".", "").replace(",", ".");
            } else {
                limpio = limpio.replace(",", "");
            }
        } else if (lastComma != -1) {
            limpio = limpio.replace(",", ".");
        }
        return Double.parseDouble(limpio);
    }

    /**
     * Lista las cuentas activas del usuario, para elegir la cuenta en un atajo antes de crear el movimiento.
     */
    @GetMapping("/cuentas")
    public ResponseEntity<?> getCuentas(HttpServletRequest request) {
        User user = authenticate(request);
        if (user == null) return unauthorized();
        return ResponseEntity.ok(encryptedCuentaService.getCuentasActivasByUser(user));
    }

    /**
     * Árbol de categorías/subcategorías, para elegir categoría en un atajo antes de crear el movimiento.
     * ?tipo=EXPENSE|INCOME filtra; sin parámetro devuelve ambos tipos.
     */
    @GetMapping("/categorias")
    public ResponseEntity<?> getCategorias(@RequestParam(required = false) String tipo, HttpServletRequest request) {
        User user = authenticate(request);
        if (user == null) return unauthorized();
        if (tipo == null || tipo.isBlank()) {
            List<CategoriaService.CategoriaDTO> ambas = new java.util.ArrayList<>();
            ambas.addAll(categoriaService.getArbolVisibleParaUsuario(user, TipoCategoria.EXPENSE));
            ambas.addAll(categoriaService.getArbolVisibleParaUsuario(user, TipoCategoria.INCOME));
            return ResponseEntity.ok(ambas);
        }
        try {
            TipoCategoria tipoCategoria = TipoCategoria.valueOf(tipo.trim().toUpperCase());
            return ResponseEntity.ok(categoriaService.getArbolVisibleParaUsuario(user, tipoCategoria));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "tipo debe ser EXPENSE o INCOME"));
        }
    }

    /**
     * Resuelve la cuenta a partir de "cuentaId", o de "cuenta" (nombre, sin distinguir mayúsculas)
     * combinado opcionalmente con "cuentaUltimosDigitos" (4 dígitos) para desambiguar cuando hay
     * varias cuentas con nombres parecidos. Ninguno de los dos es obligatorio: si no se indica nada
     * y el usuario solo tiene una cuenta activa, se usa esa por defecto.
     * Lanza IllegalArgumentException con un mensaje claro (qué cuentas tienes, o entre cuáles hay
     * que elegir) si no se puede resolver sin ambigüedad.
     */
    private Cuenta resolverCuenta(User user, Map<String, Object> payload) {
        Object cuentaIdRaw = payload.get("cuentaId");
        if (cuentaIdRaw != null) {
            Long cuentaId = Long.parseLong(cuentaIdRaw.toString());
            Cuenta cuenta = encryptedCuentaService.getCuentaById(cuentaId);
            if (cuenta == null || cuenta.getUser() == null || !cuenta.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException("No existe la cuenta con id " + cuentaId);
            }
            return cuenta;
        }

        List<EncryptedCuentaService.CuentaDTO> activas = encryptedCuentaService.getCuentasActivasByUser(user);
        Object cuentaNombreRaw = payload.get("cuenta");
        Object digitosRaw = payload.get("cuentaUltimosDigitos");
        String nombre = (cuentaNombreRaw != null && !cuentaNombreRaw.toString().isBlank()) ? cuentaNombreRaw.toString().trim() : null;
        String digitos = (digitosRaw != null && !digitosRaw.toString().isBlank()) ? digitosRaw.toString().trim() : null;

        if (nombre != null || digitos != null) {
            List<EncryptedCuentaService.CuentaDTO> candidatas = activas.stream()
                    .filter(c -> nombre == null || c.getNombre().equalsIgnoreCase(nombre))
                    .filter(c -> digitos == null || digitos.equals(c.getUltimosDigitos()))
                    .collect(java.util.stream.Collectors.toList());
            if (candidatas.size() == 1) {
                return encryptedCuentaService.getCuentaById(candidatas.get(0).getId());
            }
            if (candidatas.size() > 1) {
                String detalle = candidatas.stream()
                        .map(this::describirCuenta)
                        .reduce((a, b) -> a + ", " + b).orElse("");
                throw new IllegalArgumentException("Hay varias cuentas que coinciden: " + detalle
                        + ". Añade \"cuentaUltimosDigitos\" con los 4 últimos dígitos para distinguirlas.");
            }
            String nombres = activas.stream().map(this::describirCuenta).reduce((a, b) -> a + ", " + b).orElse("");
            throw new IllegalArgumentException("No encuentro esa cuenta. Tus cuentas: " + nombres);
        }

        // Ni nombre ni dígitos: si solo hay una cuenta activa, se usa por defecto (igual que en la web)
        if (activas.size() == 1) {
            return encryptedCuentaService.getCuentaById(activas.get(0).getId());
        }
        if (activas.isEmpty()) {
            throw new IllegalArgumentException("No tienes ninguna cuenta creada todavía. Crea una en la app antes de usar el atajo.");
        }
        String nombres = activas.stream().map(this::describirCuenta).reduce((a, b) -> a + ", " + b).orElse("");
        throw new IllegalArgumentException("Tienes varias cuentas, indica \"cuenta\": " + nombres);
    }

    private String describirCuenta(EncryptedCuentaService.CuentaDTO c) {
        return c.getUltimosDigitos() != null ? c.getNombre() + " (····" + c.getUltimosDigitos() + ")" : c.getNombre();
    }

    /**
     * Resuelve solo la categoría (sin comprobar todavía si es de ingreso o gasto) a partir de
     * "categoriaId" o "categoria" (nombre, sin distinguir mayúsculas) del payload.
     * Devuelve null si no se indicó ninguna. Lanza IllegalArgumentException si se indicó algo
     * que no existe.
     */
    private Categoria resolverSoloCategoria(Map<String, Object> payload, User user) {
        Object categoriaIdRaw = payload.get("categoriaId");
        Object categoriaNombreRaw = payload.get("categoria");
        if (categoriaIdRaw != null) {
            Long categoriaId = Long.parseLong(categoriaIdRaw.toString());
            Categoria categoria = categoriaRepository.findById(categoriaId).orElse(null);
            // Una categoría propia de otro usuario nunca se resuelve aquí, aunque el id exista de
            // verdad en BD: evitaría que un atajo etiquetara el gasto con la categoría
            // personalizada de otra cuenta solo por adivinar/probar su id.
            boolean pertenece = categoria != null && (categoria.getUser() == null || categoria.getUser().getId().equals(user.getId()));
            if (categoria == null || !pertenece) {
                throw new IllegalArgumentException("No existe la categoría con id " + categoriaId);
            }
            return categoria;
        }
        if (categoriaNombreRaw != null && !categoriaNombreRaw.toString().isBlank()) {
            String nombre = categoriaNombreRaw.toString().trim();
            Categoria categoria = categoriaRepository.findByNombreIgnoreCaseVisiblePara(nombre, user).orElse(null);
            if (categoria == null) {
                String validas = java.util.stream.Stream.concat(
                                categoriaRepository.findVisiblesPorTipo(TipoCategoria.EXPENSE, user).stream(),
                                categoriaRepository.findVisiblesPorTipo(TipoCategoria.INCOME, user).stream())
                        .map(Categoria::getNombre)
                        .reduce((a, b) -> a + ", " + b).orElse("");
                throw new IllegalArgumentException("No existe la categoría \"" + nombre + "\". Categorías válidas: " + validas);
            }
            return categoria;
        }
        return null;
    }

    /**
     * Comprueba que la categoría ya resuelta coincide con el tipo del movimiento (ingreso/gasto)
     * y resuelve además la subcategoría a partir de "subcategoriaId" o "subcategoria" (nombre).
     * Lanza IllegalArgumentException con un mensaje claro si algo no encaja.
     */
    private void validarYResolverSubcategoria(Categoria categoria, boolean ingreso, Map<String, Object> payload, Subcategoria[] subOut) {
        TipoCategoria tipoEsperado = ingreso ? TipoCategoria.INCOME : TipoCategoria.EXPENSE;
        if (categoria.getTipo() != tipoEsperado) {
            String tipoCategoriaTexto = categoria.getTipo() == TipoCategoria.INCOME ? "ingresos" : "gastos";
            String tipoMovimientoTexto = ingreso ? "un ingreso" : "un gasto";
            throw new IllegalArgumentException("La categoría \"" + categoria.getNombre() + "\" es de "
                    + tipoCategoriaTexto + " y no se puede usar en " + tipoMovimientoTexto);
        }

        Object subcategoriaIdRaw = payload.get("subcategoriaId");
        Object subcategoriaNombreRaw = payload.get("subcategoria");
        if (subcategoriaIdRaw != null) {
            Long subcategoriaId = Long.parseLong(subcategoriaIdRaw.toString());
            Subcategoria sub = subcategoriaRepository.findById(subcategoriaId).orElse(null);
            if (sub == null || !sub.getCategoria().getId().equals(categoria.getId())) {
                throw new IllegalArgumentException("La subcategoría indicada no pertenece a \"" + categoria.getNombre() + "\"");
            }
            subOut[0] = sub;
        } else if (subcategoriaNombreRaw != null && !subcategoriaNombreRaw.toString().isBlank()) {
            String nombre = subcategoriaNombreRaw.toString().trim();
            Subcategoria sub = subcategoriaRepository.findByCategoriaAndNombreIgnoreCase(categoria, nombre).orElse(null);
            if (sub == null) {
                String validas = subcategoriaRepository.findByCategoriaOrderByOrdenAsc(categoria).stream()
                        .map(Subcategoria::getNombre)
                        .reduce((a, b) -> a + ", " + b).orElse("");
                throw new IllegalArgumentException("\"" + categoria.getNombre() + "\" no tiene la subcategoría \"" + nombre
                        + "\". Subcategorías válidas: " + validas);
            }
            subOut[0] = sub;
        }
    }

    /**
     * Crea un ingreso o gasto. Payload mínimo: {"cantidad":12.5,"asunto":"...","categoria":"Alimentación","cuenta":"Santander"}.
     * Pensado para un atajo con 4 campos: Importe, Asunto, Categoría (elegida de un menú fijo) y Banco (texto libre):
     * - "cantidad": obligatoria.
     * - "asunto": opcional.
     * - "categoriaId"/"categoria" (nombre) y "subcategoriaId"/"subcategoria" (nombre): opcionales.
     * - "cuentaId" o "cuenta" (nombre, tal cual lo escriba el usuario): si se omite y solo hay una cuenta activa, se usa esa.
     *   "cuentaUltimosDigitos" (4 dígitos) es opcional y solo hace falta si el nombre no basta para distinguir
     *   entre dos cuentas (nunca es obligatorio para crear el movimiento).
     * - "fecha" (yyyy-MM-dd): si se omite, hoy. mesAsignado/anioAsignado se calculan siempre a partir de la fecha.
     * - "ingreso" (true/false): opcional. Si no se indica, se deduce de la categoría elegida (p. ej. "Ingresos" → true,
     *   cualquier categoría de gasto → false); si tampoco hay categoría, se asume gasto.
     */
    @PostMapping("/movimientos")
    public ResponseEntity<?> addMovimiento(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        User user = authenticate(request);
        if (user == null) return unauthorized();

        try {
            if (payload.get("cantidad") == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "\"cantidad\" es obligatoria"));
            }
            double cantidad;
            try {
                cantidad = parseCantidad(payload.get("cantidad"));
            } catch (NumberFormatException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error",
                        "\"cantidad\" debe ser un número (p. ej. 12.5 o \"12,50 €\")"));
            }
            String asunto = payload.get("asunto") != null ? payload.get("asunto").toString() : "";

            LocalDate fecha;
            if (payload.get("fecha") != null && !payload.get("fecha").toString().isBlank()) {
                try {
                    fecha = LocalDate.parse(payload.get("fecha").toString(), DATE_FORMATTER);
                } catch (Exception e) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "\"fecha\" debe tener formato yyyy-MM-dd"));
                }
            } else {
                fecha = LocalDate.now(java.time.ZoneId.of("Europe/Madrid"));
            }

            Cuenta cuenta;
            try {
                cuenta = resolverCuenta(user, payload);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
            }

            Categoria categoria;
            Subcategoria[] subOut = new Subcategoria[1];
            try {
                categoria = resolverSoloCategoria(payload, user);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
            }

            boolean ingreso;
            if (payload.get("ingreso") != null) {
                ingreso = Boolean.parseBoolean(payload.get("ingreso").toString());
            } else if (categoria != null) {
                ingreso = categoria.getTipo() == TipoCategoria.INCOME;
            } else {
                ingreso = false; // sin categoría ni tipo indicado: se asume gasto, el caso de uso más habitual
            }

            if (categoria != null) {
                try {
                    validarYResolverSubcategoria(categoria, ingreso, payload, subOut);
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
                }
            }

            var movimiento = encryptedMovimientoService.createMovimiento(
                    user, cuenta, cantidad, ingreso, asunto, fecha,
                    fecha.getMonthValue(), fecha.getYear(),
                    categoria != null ? categoria.getId() : null,
                    subOut[0] != null ? subOut[0].getId() : null);

            var dto = encryptedMovimientoService.convertToDTO(movimiento);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "movimiento", dto,
                    "balanceTotal", encryptedCuentaService.getBalanceTotal(user)
            ));
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "\"cantidad\" debe ser un número"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/movimientos")
    public ResponseEntity<?> getMovimientos(HttpServletRequest request) {
        User user = authenticate(request);
        if (user == null) return unauthorized();

        try {
            var movimientos = encryptedMovimientoService.getMovimientosByUser(user);
            double balanceTotal = encryptedCuentaService.getBalanceTotal(user);
            double totalIngresos = encryptedMovimientoService.getTotalIngresos(user);
            double totalGastos = encryptedMovimientoService.getTotalGastos(user);

            Map<String, Object> response = Map.of(
                "movimientos", movimientos,
                "balanceTotal", balanceTotal,
                "totalIngresos", totalIngresos,
                "totalGastos", totalGastos
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/movimientos/resumen")
    public ResponseEntity<?> getResumen(HttpServletRequest request) {
        User user = authenticate(request);
        if (user == null) return unauthorized();

        try {
            double balanceTotal = encryptedCuentaService.getBalanceTotal(user);
            double totalIngresos = encryptedMovimientoService.getTotalIngresos(user);
            double totalGastos = encryptedMovimientoService.getTotalGastos(user);

            Map<String, Object> response = Map.of(
                "balanceTotal", balanceTotal,
                "totalIngresos", totalIngresos,
                "totalGastos", totalGastos
            );

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}
