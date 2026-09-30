package com.balancdapp.controller;

import com.balancdapp.model.Cuenta;
import com.balancdapp.model.TipoCuenta;
import com.balancdapp.model.User;
import com.balancdapp.service.*;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Endpoints JSON (autenticados con JWT) que replican las acciones de los controladores web,
 * para la app nativa de iOS. Reutilizan exactamente los mismos servicios que la web.
 */
@RestController
@RequestMapping("/api")
public class ApiAppController {
    @Autowired private JwtService jwtService;
    @Autowired private UserService userService;
    @Autowired private EncryptedCuentaService cuentaService;
    @Autowired private EncryptedMovimientoService movService;
    @Autowired private MovimientoService movimientoService;
    @Autowired private EncryptedMovimientoRecurrenteService recurrenteService;
    @Autowired private EncryptedTransferenciaService transferenciaService;

    private User auth(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ")) return null;
        Claims c = jwtService.validateToken(h.substring(7));
        if (c == null) return null;
        User u = userService.getUserById(((Number) c.get("id")).longValue()).orElse(null);
        return (u != null && u.isBaneado()) ? null : u;
    }

    /** Ejecuta la acción autenticada y convierte excepciones en 400 con {"error":...}. */
    private ResponseEntity<?> run(HttpServletRequest req, java.util.function.Function<User, Object> action) {
        User user = auth(req);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token no proporcionado o inválido"));
        try {
            Object r = action.apply(user);
            return ResponseEntity.ok(r == null ? Map.of("success", true) : r);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error"));
        }
    }

    private Cuenta owned(Long id, User user) {
        Cuenta c = id == null ? null : cuentaService.getCuentaById(id);
        if (c == null || c.getUser() == null || !c.getUser().getId().equals(user.getId()))
            throw new IllegalArgumentException("Cuenta no válida");
        return c;
    }
    private static Long lng(Object o) { return (o == null || o.toString().isBlank()) ? null : Long.parseLong(o.toString()); }
    private static double dbl(Object o) { return o == null || o.toString().isBlank() ? 0 : Double.parseDouble(o.toString().replace(",", ".")); }
    private static String str(Object o) { return o == null ? "" : o.toString().trim(); }

    // ---------- Movimientos ----------
    @PostMapping("/movimientos/{id}/editar")
    public ResponseEntity<?> editarMovimiento(@PathVariable Long id, @RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            var mov = movimientoService.getMovimientoById(id);
            if (mov == null || !mov.getUser().getId().equals(user.getId())) throw new IllegalArgumentException("Movimiento no encontrado");
            Cuenta cuenta = p.get("cuentaId") != null ? owned(lng(p.get("cuentaId")), user) : mov.getCuenta();
            movService.updateMovimiento(mov, cuenta, dbl(p.get("cantidad")), str(p.get("asunto")),
                    Boolean.parseBoolean(str(p.get("ingreso"))), LocalDate.parse(str(p.get("fecha"))),
                    lng(p.get("categoriaId")), lng(p.get("subcategoriaId")));
            return null;
        });
    }

    @DeleteMapping("/movimientos/{id}")
    public ResponseEntity<?> borrarMovimiento(@PathVariable Long id, HttpServletRequest req) {
        return run(req, user -> { movimientoService.deleteMovimiento(id, user.getId()); return null; });
    }

    // ---------- Meses manuales ----------
    @GetMapping("/meses")
    public ResponseEntity<?> meses(HttpServletRequest req) {
        return run(req, user -> movimientoService.getMesesManuales(user.getId()).stream()
                .map(ym -> Map.of("mes", ym.getMonthValue(), "anio", ym.getYear())).toList());
    }

    @PostMapping("/meses")
    public ResponseEntity<?> crearMes(@RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> { movimientoService.crearMesManual(user.getId(), (int) dbl(p.get("mes")), (int) dbl(p.get("anio"))); return null; });
    }

    @PostMapping("/meses/eliminar")
    public ResponseEntity<?> eliminarMes(@RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            int mes = (int) dbl(p.get("mes")), anio = (int) dbl(p.get("anio"));
            movimientoService.getMovimientosByUserId(user.getId()).stream()
                    .filter(m -> m.getMesAsignado() == mes && m.getAnioAsignado() == anio)
                    .forEach(m -> movimientoService.deleteMovimiento(m.getId(), user.getId()));
            movimientoService.eliminarMesManual(user.getId(), mes, anio);
            return null;
        });
    }

    // ---------- Cuentas ----------
    @GetMapping("/cuentas/todas")
    public ResponseEntity<?> todasCuentas(HttpServletRequest req) {
        return run(req, user -> cuentaService.getCuentasByUser(user));
    }

    private static TipoCuenta tipo(Object o) {
        try { return TipoCuenta.valueOf(str(o)); } catch (Exception e) { throw new IllegalArgumentException("Tipo de cuenta inválido"); }
    }
    private static String digitos(Object o) {
        String s = str(o);
        if (s.isEmpty()) return null;
        if (!s.matches("\\d{4}")) throw new IllegalArgumentException("Los últimos dígitos deben ser exactamente 4 números.");
        return s;
    }

    @GetMapping("/cuentas/tipos")
    public ResponseEntity<?> tiposCuenta(HttpServletRequest req) {
        return run(req, user -> java.util.Arrays.stream(TipoCuenta.values()).map(Enum::name).toList());
    }

    @PostMapping("/cuentas")
    public ResponseEntity<?> crearCuenta(@RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            if (str(p.get("nombre")).isEmpty()) throw new IllegalArgumentException("El nombre de la cuenta no puede estar vacío.");
            cuentaService.crearCuenta(user, str(p.get("nombre")), tipo(p.get("tipo")), dbl(p.get("saldoInicial")), digitos(p.get("ultimosDigitos")));
            return null;
        });
    }

    @PostMapping("/cuentas/{id}/editar")
    public ResponseEntity<?> editarCuenta(@PathVariable Long id, @RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            if (str(p.get("nombre")).isEmpty()) throw new IllegalArgumentException("El nombre de la cuenta no puede estar vacío.");
            cuentaService.actualizarCuenta(owned(id, user), str(p.get("nombre")), tipo(p.get("tipo")), dbl(p.get("saldoInicial")), digitos(p.get("ultimosDigitos")));
            return null;
        });
    }

    @PostMapping("/cuentas/{id}/desactivar")
    public ResponseEntity<?> desactivar(@PathVariable Long id, HttpServletRequest req) {
        return run(req, user -> { cuentaService.desactivar(owned(id, user)); return null; });
    }

    @PostMapping("/cuentas/{id}/activar")
    public ResponseEntity<?> activar(@PathVariable Long id, HttpServletRequest req) {
        return run(req, user -> { cuentaService.activar(owned(id, user)); return null; });
    }

    @PostMapping("/cuentas/{id}/eliminar")
    public ResponseEntity<?> eliminarCuenta(@PathVariable Long id, HttpServletRequest req) {
        return run(req, user -> {
            if (!cuentaService.eliminarSiEstaVacia(owned(id, user)))
                throw new IllegalArgumentException("No se puede eliminar una cuenta con movimientos o transferencias. Desactívala en su lugar.");
            return null;
        });
    }

    // ---------- Transferencias ----------
    @GetMapping("/transferencias")
    public ResponseEntity<?> transferencias(HttpServletRequest req) {
        return run(req, user -> transferenciaService.getTransferenciasByUser(user));
    }

    @PostMapping("/cuentas/transferir")
    public ResponseEntity<?> transferir(@RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            transferenciaService.crearTransferencia(user, owned(lng(p.get("cuentaOrigenId")), user), owned(lng(p.get("cuentaDestinoId")), user),
                    dbl(p.get("importe")), LocalDate.parse(str(p.get("fecha"))), str(p.get("descripcion")));
            return null;
        });
    }

    @DeleteMapping("/transferencias/{id}")
    public ResponseEntity<?> borrarTransferencia(@PathVariable Long id, HttpServletRequest req) {
        return run(req, user -> {
            var t = transferenciaService.getById(id);
            if (t == null || !t.getUser().getId().equals(user.getId())) throw new IllegalArgumentException("Transferencia no encontrada.");
            transferenciaService.eliminar(t);
            return null;
        });
    }

    // ---------- Recurrentes ----------
    @GetMapping("/recurrentes")
    public ResponseEntity<?> recurrentes(HttpServletRequest req) {
        return run(req, user -> recurrenteService.getRecurrentesByUser(user));
    }

    @PostMapping("/recurrentes")
    public ResponseEntity<?> crearRecurrente(@RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            String ff = str(p.get("fechaFin"));
            Integer rep = p.get("repeticiones") == null || str(p.get("repeticiones")).isEmpty() ? null : (int) dbl(p.get("repeticiones"));
            movService.crearMovimientoRecurrente(user, owned(lng(p.get("cuentaId")), user), dbl(p.get("cantidad")),
                    Boolean.parseBoolean(str(p.get("ingreso"))), str(p.get("asunto")), LocalDate.parse(str(p.get("fecha"))),
                    str(p.get("frecuencia")), rep, ff.isEmpty() ? null : LocalDate.parse(ff),
                    lng(p.get("categoriaId")), lng(p.get("subcategoriaId")));
            return null;
        });
    }

    @PostMapping("/recurrentes/{id}/terminar")
    public ResponseEntity<?> terminarRecurrente(@PathVariable Long id, HttpServletRequest req) {
        return run(req, user -> { movService.terminarMovimientoRecurrente(id, user); return null; });
    }

    @PostMapping("/recurrentes/{id}/borrar")
    public ResponseEntity<?> borrarRecurrente(@PathVariable Long id, HttpServletRequest req) {
        return run(req, user -> { movService.borrarMovimientoRecurrente(id, user); return null; });
    }

    @PostMapping("/recurrentes/{id}/modificar")
    public ResponseEntity<?> modificarRecurrente(@PathVariable Long id, @RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            movService.modificarMovimientoRecurrente(id, user, owned(lng(p.get("cuentaId")), user), dbl(p.get("cantidad")), str(p.get("asunto")),
                    Boolean.parseBoolean(str(p.get("ingreso"))), LocalDate.parse(str(p.get("fechaInicio"))),
                    str(p.get("frecuencia")), lng(p.get("categoriaId")), lng(p.get("subcategoriaId")));
            return null;
        });
    }
}
