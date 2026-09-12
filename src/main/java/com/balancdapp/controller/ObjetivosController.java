package com.balancdapp.controller;

import com.balancdapp.model.Categoria;
import com.balancdapp.model.Cuenta;
import com.balancdapp.model.Objetivo;
import com.balancdapp.model.Presupuesto;
import com.balancdapp.model.Subcategoria;
import com.balancdapp.model.TipoCategoria;
import com.balancdapp.model.User;
import com.balancdapp.repository.CategoriaRepository;
import com.balancdapp.repository.ObjetivoRepository;
import com.balancdapp.repository.PresupuestoRepository;
import com.balancdapp.repository.SubcategoriaRepository;
import com.balancdapp.service.CategoriaService;
import com.balancdapp.service.EncryptedCuentaService;
import com.balancdapp.service.EncryptedObjetivoService;
import com.balancdapp.service.EncryptedPresupuestoService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Controller
public class ObjetivosController {

    @Autowired
    private EncryptedPresupuestoService encryptedPresupuestoService;

    @Autowired
    private EncryptedObjetivoService encryptedObjetivoService;

    @Autowired
    private EncryptedCuentaService encryptedCuentaService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private SubcategoriaRepository subcategoriaRepository;

    @Autowired
    private CategoriaService categoriaService;

    @Autowired
    private PresupuestoRepository presupuestoRepository;

    @Autowired
    private ObjetivoRepository objetivoRepository;

    @GetMapping("/objetivos")
    public String verObjetivos(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        YearMonth actual = YearMonth.now();
        List<EncryptedPresupuestoService.PresupuestoDTO> presupuestos =
                encryptedPresupuestoService.getPresupuestosConProgreso(user, actual.getMonthValue(), actual.getYear());
        List<EncryptedObjetivoService.ObjetivoDTO> objetivos = encryptedObjetivoService.getObjetivosConProgreso(user);

        model.addAttribute("presupuestos", presupuestos);
        model.addAttribute("objetivos", objetivos);
        model.addAttribute("categoriasGasto", categoriaService.getArbolVisibleParaUsuario(user, TipoCategoria.EXPENSE));
        model.addAttribute("cuentas", encryptedCuentaService.getCuentasActivasByUser(user));
        model.addAttribute("mesActual", actual);
        return "objetivos";
    }

    /**
     * Resuelve categoría (opcional) y subcategoría (opcional, debe pertenecer a la categoría)
     * a partir de los ids del formulario. Lanza IllegalArgumentException con un mensaje claro
     * si la combinación no es válida.
     */
    private Categoria[] resolverCategoriaYSubcategoria(Long categoriaId, Long subcategoriaId, Subcategoria[] subOut, User user) {
        Categoria categoria = null;
        if (categoriaId != null) {
            categoria = categoriaRepository.findById(categoriaId).orElse(null);
            // Una categoría propia de otro usuario no es "no válida" por casualidad: nunca debe
            // poder resolverse desde aquí, para que nadie etiquete su presupuesto con la
            // categoría personalizada de otra cuenta manipulando el id del formulario.
            if (categoria == null || (categoria.getUser() != null && !categoria.getUser().getId().equals(user.getId()))) {
                throw new IllegalArgumentException("Categoría no válida.");
            }
        }
        if (subcategoriaId != null) {
            Subcategoria sub = subcategoriaRepository.findById(subcategoriaId).orElse(null);
            if (sub == null || categoria == null || !sub.getCategoria().getId().equals(categoria.getId())) {
                throw new IllegalArgumentException("La subcategoría no pertenece a la categoría elegida.");
            }
            subOut[0] = sub;
        }
        return new Categoria[]{categoria};
    }

    private Presupuesto requireOwnedPresupuesto(Long id, User user) {
        Presupuesto p = presupuestoRepository.findById(id).orElse(null);
        if (p == null || p.getUser() == null || !p.getUser().getId().equals(user.getId())) {
            return null;
        }
        return p;
    }

    private Objetivo requireOwnedObjetivo(Long id, User user) {
        Objetivo o = objetivoRepository.findById(id).orElse(null);
        if (o == null || o.getUser() == null || !o.getUser().getId().equals(user.getId())) {
            return null;
        }
        return o;
    }

    private Cuenta requireOwnedCuenta(Long cuentaId, User user) {
        if (cuentaId == null) return null;
        Cuenta cuenta = encryptedCuentaService.getCuentaById(cuentaId);
        if (cuenta == null || cuenta.getUser() == null || !cuenta.getUser().getId().equals(user.getId())) {
            return null;
        }
        return cuenta;
    }

    @PostMapping("/objetivos/presupuestos")
    public String crearPresupuesto(@RequestParam(required = false) Long categoriaId,
                                   @RequestParam(required = false) Long subcategoriaId,
                                   @RequestParam(required = false) String concepto,
                                   @RequestParam String limite,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        try {
            Subcategoria[] subOut = new Subcategoria[1];
            Categoria categoria = resolverCategoriaYSubcategoria(categoriaId, subcategoriaId, subOut, user)[0];
            double limiteParsed = Double.parseDouble(limite.replace(",", "."));
            encryptedPresupuestoService.crearPresupuesto(user, categoria, subOut[0], concepto, limiteParsed);
            redirectAttributes.addFlashAttribute("success", "Presupuesto creado correctamente.");
        } catch (NumberFormatException e) {
            redirectAttributes.addFlashAttribute("error", "El límite debe ser un número.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/objetivos";
    }

    @PostMapping("/objetivos/presupuestos/{id}/editar")
    public String editarPresupuesto(@PathVariable Long id,
                                    @RequestParam(required = false) Long categoriaId,
                                    @RequestParam(required = false) Long subcategoriaId,
                                    @RequestParam(required = false) String concepto,
                                    @RequestParam String limite,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        Presupuesto presupuesto = requireOwnedPresupuesto(id, user);
        if (presupuesto == null) {
            redirectAttributes.addFlashAttribute("error", "Presupuesto no encontrado.");
            return "redirect:/objetivos";
        }
        try {
            Subcategoria[] subOut = new Subcategoria[1];
            Categoria categoria = resolverCategoriaYSubcategoria(categoriaId, subcategoriaId, subOut, user)[0];
            double limiteParsed = Double.parseDouble(limite.replace(",", "."));
            encryptedPresupuestoService.actualizarPresupuesto(presupuesto, categoria, subOut[0], concepto, limiteParsed);
            redirectAttributes.addFlashAttribute("success", "Presupuesto actualizado correctamente.");
        } catch (NumberFormatException e) {
            redirectAttributes.addFlashAttribute("error", "El límite debe ser un número.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/objetivos";
    }

    @PostMapping("/objetivos/presupuestos/{id}/eliminar")
    public String eliminarPresupuesto(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        Presupuesto presupuesto = requireOwnedPresupuesto(id, user);
        if (presupuesto != null) {
            encryptedPresupuestoService.eliminar(presupuesto);
            redirectAttributes.addFlashAttribute("success", "Presupuesto eliminado.");
        }
        return "redirect:/objetivos";
    }

    @PostMapping("/objetivos/metas")
    public String crearObjetivo(@RequestParam Long cuentaId,
                                @RequestParam String nombre,
                                @RequestParam String objetivo,
                                @RequestParam(required = false) String fechaLimite,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        Cuenta cuenta = requireOwnedCuenta(cuentaId, user);
        if (cuenta == null) {
            redirectAttributes.addFlashAttribute("error", "Selecciona una cuenta válida.");
            return "redirect:/objetivos";
        }
        if (nombre == null || nombre.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "El objetivo necesita un nombre.");
            return "redirect:/objetivos";
        }
        try {
            double objetivoParsed = Double.parseDouble(objetivo.replace(",", "."));
            LocalDate fecha = (fechaLimite != null && !fechaLimite.isBlank()) ? LocalDate.parse(fechaLimite) : null;
            encryptedObjetivoService.crearObjetivo(user, cuenta, nombre.trim(), objetivoParsed, fecha);
            redirectAttributes.addFlashAttribute("success", "Objetivo creado correctamente.");
        } catch (NumberFormatException e) {
            redirectAttributes.addFlashAttribute("error", "El importe objetivo debe ser un número.");
        }
        return "redirect:/objetivos";
    }

    @PostMapping("/objetivos/metas/{id}/editar")
    public String editarObjetivo(@PathVariable Long id,
                                 @RequestParam Long cuentaId,
                                 @RequestParam String nombre,
                                 @RequestParam String objetivo,
                                 @RequestParam(required = false) String fechaLimite,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        Objetivo o = requireOwnedObjetivo(id, user);
        Cuenta cuenta = requireOwnedCuenta(cuentaId, user);
        if (o == null || cuenta == null) {
            redirectAttributes.addFlashAttribute("error", "Objetivo o cuenta no válidos.");
            return "redirect:/objetivos";
        }
        try {
            double objetivoParsed = Double.parseDouble(objetivo.replace(",", "."));
            LocalDate fecha = (fechaLimite != null && !fechaLimite.isBlank()) ? LocalDate.parse(fechaLimite) : null;
            encryptedObjetivoService.actualizarObjetivo(o, cuenta, nombre.trim(), objetivoParsed, fecha);
            redirectAttributes.addFlashAttribute("success", "Objetivo actualizado correctamente.");
        } catch (NumberFormatException e) {
            redirectAttributes.addFlashAttribute("error", "El importe objetivo debe ser un número.");
        }
        return "redirect:/objetivos";
    }

    @PostMapping("/objetivos/metas/{id}/eliminar")
    public String eliminarObjetivo(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) return "redirect:/login";

        Objetivo o = requireOwnedObjetivo(id, user);
        if (o != null) {
            encryptedObjetivoService.eliminar(o);
            redirectAttributes.addFlashAttribute("success", "Objetivo eliminado.");
        }
        return "redirect:/objetivos";
    }
}
