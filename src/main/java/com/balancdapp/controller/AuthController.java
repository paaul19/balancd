package com.balancdapp.controller;

import com.balancdapp.dto.LoginRequest;
import com.balancdapp.dto.RegisterRequest;
import com.balancdapp.model.User;
import com.balancdapp.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    @Autowired
    private UserService userService;

    @GetMapping("/")
    public String home(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user != null) {
            return "redirect:/movimientos";
        } else {
            return "landing";
        }
    }

    @GetMapping("/login")
    public String loginForm(@RequestParam(value = "banned", required = false) String banned, Model model) {
        model.addAttribute("user", new User());
        if (banned != null) {
            model.addAttribute("error", "Tu cuenta ha sido suspendida.");
        }
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@ModelAttribute LoginRequest loginRequest, HttpSession session, RedirectAttributes redirectAttributes) {
        return userService.authenticateUser(loginRequest.getUsername(), loginRequest.getPassword())
                .map(authenticatedUser -> {
                    if (!authenticatedUser.isVerified()) {
                        redirectAttributes.addFlashAttribute("error", "Debes verificar tu correo antes de iniciar sesión.");
                        return "redirect:/login";
                    }
                    if (authenticatedUser.isBaneado()) {
                        redirectAttributes.addFlashAttribute("error", "Tu cuenta ha sido suspendida.");
                        return "redirect:/login";
                    }
                    session.setAttribute("user", authenticatedUser);
                    return "redirect:/movimientos";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Invalid username or password");
                    return "redirect:/login";
                });
    }


    @PostMapping("/auth/register")
    public String register(@ModelAttribute RegisterRequest registerRequest, RedirectAttributes redirectAttributes, Model model) {
        try {
            if (userService.getUserByUsername(registerRequest.getUsername()).isPresent()) {
                model.addAttribute("error", "Username already in use");
                return "error";
            }
            // Deliberadamente NO se revela si el email ya está en uso (ver M6 en la auditoría):
            // registerUser() ya lo comprueba internamente y lanza una RuntimeException genérica
            // que se muestra igual que cualquier otro fallo de validación.

            // Se construye una entidad User nueva a mano, copiando solo los 3 campos permitidos.
            // Nunca se bindea la entidad JPA directamente desde el formulario: así "id" (u otro
            // campo interno como isVerified) no puede llegar desde la petición del cliente.
            User user = new User();
            user.setUsername(registerRequest.getUsername());
            user.setEmail(registerRequest.getEmail());
            user.setPassword(registerRequest.getPassword());

            userService.registerUser(user);
            model.addAttribute("email", user.getEmail());
            return "auth/check-email";
        } catch (RuntimeException e) {
            // Nota: no existe un @GetMapping("/auth/register") propio (el formulario de
            // registro vive como pestaña dentro de /login) - se redirige ahí para que el
            // mensaje de error sea visible en vez de terminar en un 404.
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/verify")
    public String verifyEmail(@RequestParam("token") String token, Model model, RedirectAttributes redirectAttributes) {
        boolean verified = userService.verifyUser(token);
        if (verified) {
            redirectAttributes.addFlashAttribute("success", "Cuenta verificada correctamente.");
            return "redirect:/login";
        } else {
            model.addAttribute("error", "Token de verificación inválido o expirado.");
            return "error";
        }
    }

    /** Hallazgo M5: aplica el cambio de email solo cuando se confirma desde la dirección nueva. */
    @GetMapping("/confirmar-cambio-email")
    public String confirmarCambioEmail(@RequestParam("token") String token, Model model, RedirectAttributes redirectAttributes) {
        User actualizado = userService.confirmEmailChange(token);
        if (actualizado != null) {
            redirectAttributes.addFlashAttribute("success", "Correo electrónico actualizado correctamente. Inicia sesión de nuevo.");
            return "redirect:/login";
        }
        model.addAttribute("error", "El enlace de confirmación no es válido, ha expirado o el correo ya está en uso.");
        return "error";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm(Model model) {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam("email") String email, Model model) {
        boolean sent = userService.sendPasswordResetToken(email);
        model.addAttribute("email", email);
        model.addAttribute("sent", sent);
        return "auth/forgot-password-confirm";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam("token") String token, Model model) {
        boolean valid = userService.isValidResetToken(token);
        model.addAttribute("token", token);
        model.addAttribute("valid", valid);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam("token") String token,
                                       @RequestParam("password") String password,
                                       Model model,
                                       RedirectAttributes redirectAttributes) {
        try {
            boolean success = userService.resetPassword(token, password);
            if (success) {
                redirectAttributes.addFlashAttribute("success", "Contraseña cambiada correctamente.");
                return "redirect:/login";
            }
            model.addAttribute("success", false);
            return "auth/reset-password-confirm";
        } catch (RuntimeException e) {
            // Contraseña demasiado corta (M2): se vuelve a mostrar el formulario, no la
            // pantalla de "token inválido", para que el usuario entienda el motivo real.
            model.addAttribute("token", token);
            model.addAttribute("valid", true);
            model.addAttribute("error", e.getMessage());
            return "auth/reset-password";
        }
    }
}