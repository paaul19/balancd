package com.balancdapp.controller;

import com.balancdapp.model.User;
import com.balancdapp.service.UserService;
import com.balancdapp.service.PasswordService;
import com.balancdapp.service.EncryptedMovimientoService;
import com.balancdapp.repository.UserRepository;
import com.balancdapp.repository.MovimientoRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;

@Controller
public class PerfilController {
    @Autowired
    private UserService userService;
    @Autowired
    private PasswordService passwordService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EncryptedMovimientoService encryptedMovimientoService;
    @Autowired
    private MovimientoRepository movimientoRepository;

    @GetMapping("/perfil")
    public String perfil(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        model.addAttribute("nombre", user.getUsername());
        // Obtener meses con movimientos
        List<Object[]> anioMeses = movimientoRepository.findDistinctYearMonthsByUser(user);
        Set<YearMonth> mesesConMovimientos = new java.util.TreeSet<>(java.util.Comparator.reverseOrder());
        for (Object[] anioMes : anioMeses) {
            Integer anio = (Integer) anioMes[0];
            Integer mes = (Integer) anioMes[1];
            mesesConMovimientos.add(YearMonth.of(anio, mes));
        }
        model.addAttribute("mesesConMovimientos", mesesConMovimientos);
        // Para cada mes, obtener movimientos descifrados y agrupar gastos por categoría
        Map<String, List<Map<String, Object>>> gastosPorCategoriaPorMes = new HashMap<>();
        for (YearMonth ym : mesesConMovimientos) {
            List<EncryptedMovimientoService.MovimientoDTO> lista = encryptedMovimientoService.getMovimientosByUserAndMesAnio(user.getId(), ym.getMonthValue(), ym.getYear());
            Map<String, Double> sumaPorCategoria = new HashMap<>();
            for (EncryptedMovimientoService.MovimientoDTO mov : lista) {
                if (!mov.isIngreso() && mov.getCategoria() != null) {
                    sumaPorCategoria.put(mov.getCategoria(), sumaPorCategoria.getOrDefault(mov.getCategoria(), 0.0) + mov.getCantidad());
                }
            }
            List<Map<String, Object>> arr = new java.util.ArrayList<>();
            for (Map.Entry<String, Double> entry : sumaPorCategoria.entrySet()) {
                String cat = entry.getKey();
                String nombreBonito;
                switch(cat) {
                    case "TRANSPORTE": nombreBonito = "Transporte"; break;
                    case "COMIDA": nombreBonito = "Comida"; break;
                    case "OCIO_ENTRETENIMIENTO": nombreBonito = "Ocio y Entretenimiento"; break;
                    case "HOGAR": nombreBonito = "Hogar"; break;
                    case "SALUD_BIENESTAR": nombreBonito = "Salud y Bienestar"; break;
                    case "EDUCACION_CURSOS": nombreBonito = "Educación y Cursos"; break;
                    case "COMPRAS": nombreBonito = "Compras"; break;
                    case "COMPRAS_ONLINE": nombreBonito = "Compras Online"; break;
                    case "SUSCRIPCION": nombreBonito = "Suscripción"; break;
                    default: nombreBonito = cat;
                }
                Map<String, Object> obj = new HashMap<>();
                obj.put("categoria", cat);
                obj.put("categoriaBonita", nombreBonito);
                obj.put("total", entry.getValue());
                arr.add(obj);
            }
            String mesKey = ym.getYear() + "-" + ym.getMonthValue();
            gastosPorCategoriaPorMes.put(mesKey, arr);
        }
        model.addAttribute("gastosPorCategoriaPorMes", gastosPorCategoriaPorMes);
        return "perfil";
    }

    // Nueva vista de ajustes
    @GetMapping("/ajustes")
    public String ajustes(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        return "ajustes";
    }

    @PostMapping("/perfil/cambiar-usuario")
    public String cambiarUsuario(@RequestParam("nuevoUsername") String nuevoUsername,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        try {
            user.setUsername(nuevoUsername);
            User actualizado = userService.updateUser(user);
            session.setAttribute("user", actualizado);
            redirectAttributes.addFlashAttribute("success", "Nombre de usuario actualizado correctamente.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/perfil";
    }

    @PostMapping("/perfil/cambiar-password")
    public String cambiarPassword(@RequestParam("passwordActual") String passwordActual,
                                  @RequestParam("nuevoPassword") String nuevoPassword,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        if (!passwordService.matches(passwordActual, user.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "La contraseña actual es incorrecta.");
            return "redirect:/perfil";
        }
        if (nuevoPassword == null || nuevoPassword.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "La nueva contraseña no puede estar vacía.");
            return "redirect:/perfil";
        }
        user.setPassword(nuevoPassword);
        User actualizado = userService.updateUser(user);
        session.setAttribute("user", actualizado);
        redirectAttributes.addFlashAttribute("success", "Contraseña actualizada correctamente.");
        return "redirect:/perfil";
    }

    @PostMapping("/perfil/cambiar-email")
    public String cambiarEmail(@RequestParam("nuevoEmail") String nuevoEmail,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        if (nuevoEmail == null || nuevoEmail.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "El correo electrónico no puede estar vacío.");
            return "redirect:/perfil";
        }
        if (userService.getUserByEmail(nuevoEmail).isPresent()) {
            redirectAttributes.addFlashAttribute("error", "Ese correo electrónico ya está en uso.");
            return "redirect:/perfil";
        }
        user.setEmail(nuevoEmail);
        User actualizado = userService.updateUser(user);
        session.setAttribute("user", actualizado);
        redirectAttributes.addFlashAttribute("success", "Correo electrónico actualizado correctamente.");
        return "redirect:/perfil";
    }

    @PostMapping("/perfil/cambiar-balance")
    public String cambiarBalance(@RequestParam("nuevoBalance") String nuevoBalance,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        try {
            BigDecimal balance = new BigDecimal(nuevoBalance.replace(",", "."));
            user.setBalanceTotal(balance);
            User actualizado = userService.updateUser(user);
            // Refrescar user en sesión
            actualizado = userService.getUserById(user.getId()).orElse(actualizado);
            session.setAttribute("user", actualizado);
            redirectAttributes.addFlashAttribute("success", "Balance total actualizado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar el balance: " + e.getMessage());
        }
        return "redirect:/perfil";
    }

    @PostMapping("/perfil/eliminar-cuenta")
    public String eliminarCuenta(HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        // Eliminar usuario y sus datos
        userRepository.deleteById(user.getId());
        session.invalidate();
        redirectAttributes.addFlashAttribute("success", "Tu cuenta ha sido eliminada correctamente.");
        return "redirect:/login";
    }
} 