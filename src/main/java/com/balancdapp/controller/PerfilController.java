package com.balancdapp.controller;

import com.balancdapp.model.User;
import com.balancdapp.service.UserService;
import com.balancdapp.service.PasswordService;
import com.balancdapp.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PerfilController {
    @Autowired
    private UserService userService;
    @Autowired
    private PasswordService passwordService;
    @Autowired
    private UserRepository userRepository;

    @GetMapping("/perfil")
    public String perfil(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        return "perfil";
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