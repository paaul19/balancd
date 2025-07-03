package com.musicstore.controller;

import com.musicstore.model.User;
import com.musicstore.service.UserService;
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

    @GetMapping("/login")
    public String loginForm(Model model) {
        model.addAttribute("user", new User());
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@ModelAttribute User user, HttpSession session, RedirectAttributes redirectAttributes) {
        return userService.authenticateUser(user.getUsername(), user.getPassword())
                .map(authenticatedUser -> {
                    session.setAttribute("user", authenticatedUser);
                    return "redirect:/";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Invalid username or password");
                    return "redirect:/login";
                });
    }


    @PostMapping("/auth/register")
    public String register(@ModelAttribute User user, RedirectAttributes redirectAttributes, Model model) {
        try {
            // Check if username already exists
            if (userService.getUserByUsername(user.getUsername()).isPresent()) {
                model.addAttribute("error", "Username already in use");
                return "error";
            }
            userService.registerUser(user);
            redirectAttributes.addFlashAttribute("success", "Registration successful. Please login.");
            return "redirect:/login";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/auth/register";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
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
            return "redirect:/";
        } else {
            model.addAttribute("error", "Contraseña incorrecta");
            return "auth/acceso";
        }
    }
}