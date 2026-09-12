package com.balancdapp.controller;

import com.balancdapp.model.TipoCategoria;
import com.balancdapp.model.User;
import com.balancdapp.service.CategoriaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** Gestión de categorías desde Ajustes: activar/desactivar y crear categorías propias. */
@Controller
public class CategoriaController {

    @Autowired
    private CategoriaService categoriaService;

    @GetMapping("/ajustes/categorias")
    public String gestionCategorias(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("categoriasGasto", categoriaService.getArbolGestionParaUsuario(user, TipoCategoria.EXPENSE));
        model.addAttribute("categoriasIngreso", categoriaService.getArbolGestionParaUsuario(user, TipoCategoria.INCOME));
        model.addAttribute("iconosDisponibles", CategoriaService.ICONOS_VALIDOS);
        return "ajustes-categorias";
    }

    @PostMapping("/ajustes/categorias/{id}/toggle")
    public String toggleCategoria(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        try {
            categoriaService.toggleCategoria(user, id);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ajustes/categorias";
    }

    @PostMapping("/ajustes/subcategorias/{id}/toggle")
    public String toggleSubcategoria(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        try {
            categoriaService.toggleSubcategoria(user, id);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ajustes/categorias";
    }

    @PostMapping("/ajustes/categorias")
    public String crearCategoria(@RequestParam String nombre,
                                 @RequestParam String icono,
                                 @RequestParam String tipo,
                                 @RequestParam(value = "subcategorias", required = false) List<String> subcategorias,
                                 HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        try {
            TipoCategoria tipoCategoria = TipoCategoria.valueOf(tipo);
            categoriaService.crearCategoriaPersonalizada(user, nombre, icono, tipoCategoria, subcategorias);
            redirectAttributes.addFlashAttribute("success", "Categoría \"" + nombre.trim() + "\" creada.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage() != null ? e.getMessage() : "Datos de categoría inválidos.");
        }
        return "redirect:/ajustes/categorias";
    }

    @PostMapping("/ajustes/categorias/{id}/editar")
    public String editarCategoria(@PathVariable Long id,
                                  @RequestParam String nombre,
                                  @RequestParam String icono,
                                  @RequestParam(value = "subcategorias", required = false) List<String> subcategorias,
                                  HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        try {
            categoriaService.editarCategoriaPersonalizada(user, id, nombre, icono, subcategorias);
            redirectAttributes.addFlashAttribute("success", "Categoría actualizada.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ajustes/categorias";
    }

    @PostMapping("/ajustes/categorias/{id}/eliminar")
    public String eliminarCategoria(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        try {
            categoriaService.eliminarCategoriaPersonalizada(user, id);
            redirectAttributes.addFlashAttribute("success", "Categoría eliminada.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/ajustes/categorias";
    }
}
