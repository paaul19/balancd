package com.balancdapp.admin.controller;

import com.balancdapp.admin.model.AdminUserView;
import com.balancdapp.admin.repository.AdminUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class AdminController {

    @Autowired
    private AdminUserRepository adminUserRepository;

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @GetMapping("/usuarios")
    public String usuarios(@RequestParam(value = "q", required = false) String q, Model model) {
        List<AdminUserView> usuarios = (q != null && !q.isBlank())
                ? adminUserRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(q.trim(), q.trim())
                : adminUserRepository.findAll();
        usuarios.sort((a, b) -> Long.compare(b.getId(), a.getId()));
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("q", q);
        return "usuarios";
    }

    @PostMapping("/usuarios/{id}/verificar")
    public String toggleVerificado(@PathVariable Long id, @RequestParam(required = false) String q,
                                    RedirectAttributes redirectAttributes) {
        AdminUserView usuario = adminUserRepository.findById(id).orElse(null);
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Usuario no encontrado.");
            return redirectTo(q);
        }
        adminUserRepository.setVerified(id, !usuario.isVerified());
        redirectAttributes.addFlashAttribute("success", "Verificación actualizada para " + usuario.getUsername() + ".");
        return redirectTo(q);
    }

    @PostMapping("/usuarios/{id}/banear")
    public String toggleBaneado(@PathVariable Long id, @RequestParam(required = false) String q,
                                 RedirectAttributes redirectAttributes) {
        AdminUserView usuario = adminUserRepository.findById(id).orElse(null);
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Usuario no encontrado.");
            return redirectTo(q);
        }
        boolean nuevoEstado = !usuario.isBaneado();
        adminUserRepository.setBaneado(id, nuevoEstado);
        redirectAttributes.addFlashAttribute("success",
                usuario.getUsername() + (nuevoEstado ? " ha sido baneado." : " ya no está baneado."));
        return redirectTo(q);
    }

    @PostMapping("/usuarios/{id}/eliminar")
    public String eliminar(@PathVariable Long id, @RequestParam(required = false) String q,
                            RedirectAttributes redirectAttributes) {
        AdminUserView usuario = adminUserRepository.findById(id).orElse(null);
        if (usuario == null) {
            redirectAttributes.addFlashAttribute("error", "Usuario no encontrado.");
            return redirectTo(q);
        }
        String nombre = usuario.getUsername();
        // Todas las tablas hijas de "users" tienen ON DELETE CASCADE a nivel de BD (ver
        // migración de arranque que corrige la FK de movimientos_recurrentes), así que basta
        // con borrar la fila del usuario para que se arrastre el resto de sus datos.
        adminUserRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("success", "Cuenta de " + nombre + " eliminada permanentemente.");
        return redirectTo(q);
    }

    private String redirectTo(String q) {
        return (q != null && !q.isBlank())
                ? "redirect:/usuarios?q=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8)
                : "redirect:/usuarios";
    }
}
