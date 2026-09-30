package com.balancdapp.controller;

import com.balancdapp.model.*;
import com.balancdapp.repository.*;
import com.balancdapp.service.*;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;

/** Objetivos, presupuestos, categorías, perfil y ajustes para la app iOS (JWT). Misma lógica que los controladores web. */
@RestController
@RequestMapping("/api")
public class ApiAjustesController {
    @Autowired private JwtService jwtService;
    @Autowired private UserService userService;
    @Autowired private PasswordService passwordService;
    @Autowired private UserRepository userRepository;
    @Autowired private EncryptedCuentaService cuentaService;
    @Autowired private EncryptedPresupuestoService presupuestoService;
    @Autowired private EncryptedObjetivoService objetivoService;
    @Autowired private PresupuestoRepository presupuestoRepository;
    @Autowired private ObjetivoRepository objetivoRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private SubcategoriaRepository subcategoriaRepository;
    @Autowired private CategoriaService categoriaService;

    private User auth(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ")) return null;
        Claims c = jwtService.validateToken(h.substring(7));
        if (c == null) return null;
        User u = userService.getUserById(((Number) c.get("id")).longValue()).orElse(null);
        return (u != null && u.isBaneado()) ? null : u;
    }

    private ResponseEntity<?> run(HttpServletRequest req, Function<User, Object> action) {
        User user = auth(req);
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token no proporcionado o inválido"));
        try {
            Object r = action.apply(user);
            return ResponseEntity.ok(r == null ? Map.of("success", true) : r);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Error"));
        }
    }

    private static Long lng(Object o) { return (o == null || o.toString().isBlank()) ? null : Long.parseLong(o.toString()); }
    private static double dbl(Object o) { return Double.parseDouble(o.toString().replace(",", ".")); }
    private static String str(Object o) { return o == null ? "" : o.toString().trim(); }

    private Cuenta cuentaDe(Long id, User user) {
        Cuenta c = id == null ? null : cuentaService.getCuentaById(id);
        if (c == null || c.getUser() == null || !c.getUser().getId().equals(user.getId())) throw new IllegalArgumentException("Selecciona una cuenta válida.");
        return c;
    }

    // ---------- Objetivos y presupuestos ----------
    @GetMapping("/objetivos")
    public ResponseEntity<?> objetivos(HttpServletRequest req) {
        return run(req, user -> {
            YearMonth ym = YearMonth.now();
            return Map.of("presupuestos", presupuestoService.getPresupuestosConProgreso(user, ym.getMonthValue(), ym.getYear()),
                    "objetivos", objetivoService.getObjetivosConProgreso(user));
        });
    }

    private Categoria categoria(Long categoriaId, Long subcategoriaId, Subcategoria[] subOut, User user) {
        Categoria categoria = null;
        if (categoriaId != null) {
            categoria = categoriaRepository.findById(categoriaId).orElse(null);
            if (categoria == null || (categoria.getUser() != null && !categoria.getUser().getId().equals(user.getId())))
                throw new IllegalArgumentException("Categoría no válida.");
        }
        if (subcategoriaId != null) {
            Subcategoria sub = subcategoriaRepository.findById(subcategoriaId).orElse(null);
            if (sub == null || categoria == null || !sub.getCategoria().getId().equals(categoria.getId()))
                throw new IllegalArgumentException("La subcategoría no pertenece a la categoría elegida.");
            subOut[0] = sub;
        }
        return categoria;
    }

    private Presupuesto presupuestoDe(Long id, User user) {
        Presupuesto p = presupuestoRepository.findById(id).orElse(null);
        if (p == null || p.getUser() == null || !p.getUser().getId().equals(user.getId())) throw new IllegalArgumentException("Presupuesto no encontrado.");
        return p;
    }

    private Objetivo objetivoDe(Long id, User user) {
        Objetivo o = objetivoRepository.findById(id).orElse(null);
        if (o == null || o.getUser() == null || !o.getUser().getId().equals(user.getId())) throw new IllegalArgumentException("Objetivo no encontrado.");
        return o;
    }

    @PostMapping("/objetivos/presupuestos")
    public ResponseEntity<?> crearPresupuesto(@RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            Subcategoria[] sub = new Subcategoria[1];
            Categoria c = categoria(lng(p.get("categoriaId")), lng(p.get("subcategoriaId")), sub, user);
            presupuestoService.crearPresupuesto(user, c, sub[0], str(p.get("concepto")), dbl(p.get("limite")));
            return null;
        });
    }

    @PostMapping("/objetivos/presupuestos/{id}/editar")
    public ResponseEntity<?> editarPresupuesto(@PathVariable Long id, @RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            Subcategoria[] sub = new Subcategoria[1];
            Categoria c = categoria(lng(p.get("categoriaId")), lng(p.get("subcategoriaId")), sub, user);
            presupuestoService.actualizarPresupuesto(presupuestoDe(id, user), c, sub[0], str(p.get("concepto")), dbl(p.get("limite")));
            return null;
        });
    }

    @PostMapping("/objetivos/presupuestos/{id}/eliminar")
    public ResponseEntity<?> eliminarPresupuesto(@PathVariable Long id, HttpServletRequest req) {
        return run(req, user -> { presupuestoService.eliminar(presupuestoDe(id, user)); return null; });
    }

    @PostMapping("/objetivos/metas")
    public ResponseEntity<?> crearMeta(@RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            if (str(p.get("nombre")).isEmpty()) throw new IllegalArgumentException("El objetivo necesita un nombre.");
            String f = str(p.get("fechaLimite"));
            objetivoService.crearObjetivo(user, cuentaDe(lng(p.get("cuentaId")), user), str(p.get("nombre")), dbl(p.get("objetivo")), f.isEmpty() ? null : LocalDate.parse(f));
            return null;
        });
    }

    @PostMapping("/objetivos/metas/{id}/editar")
    public ResponseEntity<?> editarMeta(@PathVariable Long id, @RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            String f = str(p.get("fechaLimite"));
            objetivoService.actualizarObjetivo(objetivoDe(id, user), cuentaDe(lng(p.get("cuentaId")), user), str(p.get("nombre")), dbl(p.get("objetivo")), f.isEmpty() ? null : LocalDate.parse(f));
            return null;
        });
    }

    @PostMapping("/objetivos/metas/{id}/eliminar")
    public ResponseEntity<?> eliminarMeta(@PathVariable Long id, HttpServletRequest req) {
        return run(req, user -> { objetivoService.eliminar(objetivoDe(id, user)); return null; });
    }

    // ---------- Categorías (gestión) ----------
    @GetMapping("/ajustes/categorias")
    public ResponseEntity<?> gestionCategorias(HttpServletRequest req) {
        return run(req, user -> {
            List<Object> todas = new ArrayList<>();
            todas.addAll(categoriaService.getArbolGestionParaUsuario(user, TipoCategoria.EXPENSE));
            todas.addAll(categoriaService.getArbolGestionParaUsuario(user, TipoCategoria.INCOME));
            return todas;
        });
    }

    @GetMapping("/ajustes/iconos")
    public ResponseEntity<?> iconos(HttpServletRequest req) {
        return run(req, user -> CategoriaService.ICONOS_VALIDOS);
    }

    @PostMapping("/ajustes/categorias/{id}/toggle")
    public ResponseEntity<?> toggleCategoria(@PathVariable Long id, HttpServletRequest req) {
        return run(req, user -> { categoriaService.toggleCategoria(user, id); return null; });
    }

    @PostMapping("/ajustes/subcategorias/{id}/toggle")
    public ResponseEntity<?> toggleSubcategoria(@PathVariable Long id, HttpServletRequest req) {
        return run(req, user -> { categoriaService.toggleSubcategoria(user, id); return null; });
    }

    @SuppressWarnings("unchecked")
    private static List<String> subs(Object o) { return o instanceof List<?> l ? (List<String>) l : null; }

    @PostMapping("/ajustes/categorias")
    public ResponseEntity<?> crearCategoria(@RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            categoriaService.crearCategoriaPersonalizada(user, str(p.get("nombre")), str(p.get("icono")), TipoCategoria.valueOf(str(p.get("tipo"))), subs(p.get("subcategorias")));
            return null;
        });
    }

    @PostMapping("/ajustes/categorias/{id}/editar")
    public ResponseEntity<?> editarCategoria(@PathVariable Long id, @RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            categoriaService.editarCategoriaPersonalizada(user, id, str(p.get("nombre")), str(p.get("icono")), subs(p.get("subcategorias")));
            return null;
        });
    }

    @PostMapping("/ajustes/categorias/{id}/eliminar")
    public ResponseEntity<?> eliminarCategoria(@PathVariable Long id, HttpServletRequest req) {
        return run(req, user -> { categoriaService.eliminarCategoriaPersonalizada(user, id); return null; });
    }

    // ---------- Recuperar contraseña (público, igual que POST /forgot-password de la web) ----------
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, Object> p) {
        // Siempre 200 con el mismo mensaje, exista o no el correo (evita enumerar usuarios).
        try { userService.sendPasswordResetToken(str(p.get("email"))); } catch (Exception ignored) { }
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ---------- Perfil ----------
    @GetMapping("/perfil")
    public ResponseEntity<?> perfil(HttpServletRequest req) {
        return run(req, user -> Map.of("username", user.getUsername(), "email", user.getEmail() == null ? "" : user.getEmail(),
                "moneda", user.getMoneda() == null ? "EUR" : user.getMoneda()));
    }

    @PostMapping("/ajustes/cambiar-moneda")
    public ResponseEntity<?> cambiarMoneda(@RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            if (!Set.of("EUR", "USD", "GBP").contains(str(p.get("moneda")))) throw new IllegalArgumentException("Divisa no válida.");
            user.setMoneda(str(p.get("moneda")));
            userService.updateUser(user);
            return null;
        });
    }

    @PostMapping("/perfil/cambiar-password")
    public ResponseEntity<?> cambiarPassword(@RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            if (!passwordService.matches(str(p.get("passwordActual")), user.getPassword())) throw new IllegalArgumentException("La contraseña actual es incorrecta.");
            userService.validatePassword(str(p.get("nuevoPassword")));
            user.setPassword(str(p.get("nuevoPassword")));
            userService.updateUser(user);
            return null;
        });
    }

    @PostMapping("/perfil/actualizar-identidad")
    public ResponseEntity<?> actualizarIdentidad(@RequestBody Map<String, Object> p, HttpServletRequest req) {
        return run(req, user -> {
            if (!passwordService.matches(str(p.get("passwordActual")), user.getPassword())) throw new IllegalArgumentException("La contraseña actual es incorrecta.");
            String nombre = str(p.get("nombre")), email = str(p.get("email"));
            if (!email.isEmpty() && !email.equals(user.getEmail())) userService.requestEmailChange(user, email);
            if (!nombre.isEmpty() && !nombre.equals(user.getUsername())) { user.setUsername(nombre); userService.updateUser(user); }
            return null;
        });
    }

    @PostMapping("/perfil/eliminar-cuenta")
    public ResponseEntity<?> eliminarCuenta(HttpServletRequest req) {
        return run(req, user -> { userRepository.deleteById(user.getId()); return null; });
    }
}
