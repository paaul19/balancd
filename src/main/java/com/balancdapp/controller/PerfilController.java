package com.balancdapp.controller;

import com.balancdapp.model.User;
import com.balancdapp.service.UserService;
import com.balancdapp.service.PasswordService;
import com.balancdapp.service.EncryptedCuentaService;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

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
    @Autowired
    private EncryptedCuentaService encryptedCuentaService;

    @GetMapping("/perfil")
    public String perfil(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        model.addAttribute("nombre", user.getUsername());
        String username = user.getUsername();
        String iniciales = username.length() >= 2
                ? username.substring(0, 2).toUpperCase()
                : username.substring(0, 1).toUpperCase();
        model.addAttribute("iniciales", iniciales);
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

    @GetMapping("/ajustes/atajos")
    public String ajustesAtajos(HttpSession session) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }
        return "ajustes-atajos";
    }

    @GetMapping("/ajustes/automatizacion")
    public String ajustesAutomatizacion(HttpSession session) {
        if (session.getAttribute("user") == null) {
            return "redirect:/login";
        }
        return "ajustes-automatizacion";
    }

    @PostMapping("/ajustes/cambiar-moneda")
    public String cambiarMoneda(@RequestParam("moneda") String moneda,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        Set<String> monedasValidas = Set.of("EUR", "USD", "GBP");
        if (!monedasValidas.contains(moneda)) {
            redirectAttributes.addFlashAttribute("error", "Divisa no válida.");
            return "redirect:/ajustes";
        }
        user.setMoneda(moneda);
        userService.updateUser(user);
        session.setAttribute("user", user);
        redirectAttributes.addFlashAttribute("success", "Divisa preferida actualizada.");
        return "redirect:/ajustes";
    }

    @GetMapping("/perfil/exportar-csv")
    public void exportarCsv(HttpSession session, HttpServletResponse response) throws java.io.IOException {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            response.sendRedirect("/login");
            return;
        }
        List<EncryptedMovimientoService.MovimientoDTO> movimientos = encryptedMovimientoService.getMovimientosByUserId(user.getId());
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"balancd-movimientos.csv\"");
        PrintWriter writer = response.getWriter();
        writer.write('﻿'); // BOM para que Excel detecte UTF-8
        writer.println("Fecha;Tipo;Cantidad;Asunto;Categoria;Subcategoria");
        for (EncryptedMovimientoService.MovimientoDTO mov : movimientos) {
            String asunto = mov.getAsunto() == null ? "" : mov.getAsunto().replace(";", ",");
            String categoria = mov.getCategoriaNombre() == null ? "" : mov.getCategoriaNombre();
            String subcategoria = mov.getSubcategoriaNombre() == null ? "" : mov.getSubcategoriaNombre();
            writer.printf("%s;%s;%s;%s;%s;%s%n",
                    mov.getFecha(),
                    mov.isIngreso() ? "Ingreso" : "Gasto",
                    String.format(java.util.Locale.forLanguageTag("es"), "%.2f", mov.getCantidad()),
                    asunto,
                    categoria,
                    subcategoria);
        }
        writer.flush();
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
            userService.updateUser(user);
            // Hallazgo M4: se invalida la sesión actual tras cambiar una credencial de acceso
            // y se obliga a iniciar sesión de nuevo. Si alguien hubiese robado esta sesión
            // (misma JSESSIONID), queda desconectado en el mismo momento en que el dueño
            // legítimo hace el cambio.
            session.invalidate();
            redirectAttributes.addFlashAttribute("success", "Nombre de usuario actualizado. Inicia sesión de nuevo.");
            return "redirect:/login";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/perfil";
        }
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
        try {
            userService.validatePassword(nuevoPassword); // hallazgo M2
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/perfil";
        }
        user.setPassword(nuevoPassword);
        userService.updateUser(user);
        // Hallazgo M4: igual que en cambiarUsuario - cambiar la contraseña invalida la sesión
        // actual y exige volver a iniciar sesión con la contraseña nueva.
        session.invalidate();
        redirectAttributes.addFlashAttribute("success", "Contraseña actualizada. Inicia sesión de nuevo.");
        return "redirect:/login";
    }

    @PostMapping("/perfil/cambiar-email")
    public String cambiarEmail(@RequestParam("passwordActual") String passwordActual,
                               @RequestParam("nuevoEmail") String nuevoEmail,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        // Hallazgo M5: cambiar el email ahora exige la contraseña actual (antes no se pedía),
        // y no se aplica al instante - ver requestEmailChange().
        if (!passwordService.matches(passwordActual, user.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "La contraseña actual es incorrecta.");
            return "redirect:/perfil";
        }
        try {
            userService.requestEmailChange(user, nuevoEmail);
            redirectAttributes.addFlashAttribute("success",
                    "Te hemos enviado un enlace de confirmación a " + nuevoEmail + ". Tu correo actual seguirá siendo válido hasta que lo confirmes.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/perfil";
    }

    /**
     * Modal "Editar perfil" de /perfil: cambia nombre y/o correo en una sola acción.
     * Reutiliza las mismas reglas que /perfil/cambiar-usuario y /perfil/cambiar-email
     * (contraseña actual obligatoria, el correo pasa por confirmación) pero en un único
     * envío, para no encadenar dos formularios cuando el usuario edita ambos campos a la vez.
     */
    @PostMapping("/perfil/actualizar-identidad")
    public String actualizarIdentidad(@RequestParam("nombre") String nuevoUsername,
                                      @RequestParam("email") String nuevoEmail,
                                      @RequestParam("passwordActual") String passwordActual,
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
        boolean usernameChanged = nuevoUsername != null && !nuevoUsername.equals(user.getUsername());
        boolean emailChanged = nuevoEmail != null && !nuevoEmail.equals(user.getEmail());
        try {
            if (emailChanged) {
                userService.requestEmailChange(user, nuevoEmail);
            }
            if (usernameChanged) {
                user.setUsername(nuevoUsername);
                userService.updateUser(user);
                // Cambiar el username invalida la sesión (hallazgo M4) - se hace al final para
                // que la petición de cambio de email (si también la hay) ya se haya guardado.
                session.invalidate();
                redirectAttributes.addFlashAttribute("success", emailChanged
                        ? "Nombre actualizado y enlace de confirmación enviado al nuevo correo. Inicia sesión de nuevo."
                        : "Nombre de usuario actualizado. Inicia sesión de nuevo.");
                return "redirect:/login";
            }
            redirectAttributes.addFlashAttribute("success", emailChanged
                    ? "Te hemos enviado un enlace de confirmación a " + nuevoEmail + "."
                    : "No había cambios que guardar.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
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