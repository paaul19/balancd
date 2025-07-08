package com.balancdapp.controller;

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
            return "redirect:/login";
        }
    }

    @GetMapping("/login")
    public String loginForm(Model model) {
        model.addAttribute("user", new User());
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@ModelAttribute User user, HttpSession session, RedirectAttributes redirectAttributes) {
        return userService.authenticateUser(user.getUsername(), user.getPassword())
                .map(authenticatedUser -> {
                    if (!authenticatedUser.isVerified()) {
                        redirectAttributes.addFlashAttribute("error", "Debes verificar tu correo antes de iniciar sesión.");
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
    public String register(@ModelAttribute User user, RedirectAttributes redirectAttributes, Model model) {
        try {
            if (userService.getUserByUsername(user.getUsername()).isPresent()) {
                model.addAttribute("error", "Username already in use");
                return "error";
            }
            if (userService.getUserByEmail(user.getEmail()).isPresent()) {
                model.addAttribute("error", "Email already in use");
                return "error";
            }
            userService.registerUser(user);
            model.addAttribute("email", user.getEmail());
            return "auth/check-email";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/auth/register";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/acceso";
    }

    @GetMapping("/acceso")
    public String accesoForm(Model model) {
        model.addAttribute("error", "");
        return "auth/acceso";
    }

    @PostMapping("/acceso")
    public String accesoSubmit(@RequestParam("password") String password, Model model, HttpSession session) {
        String passwordCorrecta = "1234"; // Cambia esto por la contraseña que quieras
        if (passwordCorrecta.equals(password)) {
            session.setAttribute("accesoPermitido", true);
            return "redirect:/login";
        } else {
            model.addAttribute("error", "Contraseña incorrecta");
            return "auth/acceso";
        }
    }

    @GetMapping("/verify")
    public String verifyEmail(@RequestParam("token") String token, Model model) {
        boolean verified = userService.verifyUser(token);
        if (verified) {
            model.addAttribute("message", "¡Cuenta verificada exitosamente! Ya puedes iniciar sesión.");
            return "auth/login";
        } else {
            model.addAttribute("error", "Token de verificación inválido o expirado.");
            return "error";
        }
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
                                       Model model) {
        boolean success = userService.resetPassword(token, password);
        model.addAttribute("success", success);
        return "auth/reset-password-confirm";
    }
}