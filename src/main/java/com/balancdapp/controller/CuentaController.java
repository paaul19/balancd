package com.balancdapp.controller;

import com.balancdapp.model.Cuenta;
import com.balancdapp.model.TipoCuenta;
import com.balancdapp.model.User;
import com.balancdapp.service.EncryptedCuentaService;
import com.balancdapp.service.EncryptedTransferenciaService;
import com.balancdapp.service.UserService;
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
import java.util.List;

@Controller
public class CuentaController {

    @Autowired
    private EncryptedCuentaService encryptedCuentaService;

    @Autowired
    private EncryptedTransferenciaService encryptedTransferenciaService;

    @Autowired
    private UserService userService;

    @GetMapping("/cuentas")
    public String listarCuentas(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        List<EncryptedCuentaService.CuentaDTO> cuentas = encryptedCuentaService.getCuentasByUser(user);
        double balanceTotal = cuentas.stream()
                .filter(EncryptedCuentaService.CuentaDTO::isActiva)
                .mapToDouble(EncryptedCuentaService.CuentaDTO::getSaldoActual)
                .sum();
        model.addAttribute("cuentas", cuentas);
        model.addAttribute("balanceTotal", balanceTotal);
        model.addAttribute("tiposCuenta", TipoCuenta.values());
        return "cuentas";
    }

    @PostMapping("/cuentas")
    public String crearCuenta(@RequestParam String nombre,
                              @RequestParam String tipo,
                              @RequestParam(required = false, defaultValue = "0") String saldoInicial,
                              @RequestParam(required = false) String ultimosDigitos,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        try {
            TipoCuenta tipoCuenta = parseTipo(tipo);
            double saldo = parseImporte(saldoInicial);
            if (nombre == null || nombre.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "El nombre de la cuenta no puede estar vacío.");
                return "redirect:/cuentas";
            }
            String digitos = parseUltimosDigitos(ultimosDigitos);
            encryptedCuentaService.crearCuenta(user, nombre.trim(), tipoCuenta, saldo, digitos);
            redirectAttributes.addFlashAttribute("success", "Cuenta creada correctamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage() != null ? e.getMessage() : "Datos de cuenta inválidos.");
        }
        return "redirect:/cuentas";
    }

    @PostMapping("/cuentas/{id}/editar")
    public String editarCuenta(@PathVariable Long id,
                               @RequestParam String nombre,
                               @RequestParam String tipo,
                               @RequestParam String saldoInicial,
                               @RequestParam(required = false) String ultimosDigitos,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        Cuenta cuenta = requireOwnedCuenta(id, user);
        if (cuenta == null) {
            redirectAttributes.addFlashAttribute("error", "Cuenta no encontrada.");
            return "redirect:/cuentas";
        }
        try {
            TipoCuenta tipoCuenta = parseTipo(tipo);
            double saldo = parseImporte(saldoInicial);
            if (nombre == null || nombre.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "El nombre de la cuenta no puede estar vacío.");
                return "redirect:/cuentas";
            }
            String digitos = parseUltimosDigitos(ultimosDigitos);
            encryptedCuentaService.actualizarCuenta(cuenta, nombre.trim(), tipoCuenta, saldo, digitos);
            redirectAttributes.addFlashAttribute("success", "Cuenta actualizada correctamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage() != null ? e.getMessage() : "Datos de cuenta inválidos.");
        }
        return "redirect:/cuentas";
    }

    @PostMapping("/cuentas/{id}/desactivar")
    public String desactivarCuenta(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        Cuenta cuenta = requireOwnedCuenta(id, user);
        if (cuenta != null) {
            encryptedCuentaService.desactivar(cuenta);
            redirectAttributes.addFlashAttribute("success", "Cuenta desactivada.");
        }
        return "redirect:/cuentas";
    }

    @PostMapping("/cuentas/{id}/activar")
    public String activarCuenta(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        Cuenta cuenta = requireOwnedCuenta(id, user);
        if (cuenta != null) {
            encryptedCuentaService.activar(cuenta);
            redirectAttributes.addFlashAttribute("success", "Cuenta reactivada.");
        }
        return "redirect:/cuentas";
    }

    @PostMapping("/cuentas/{id}/eliminar")
    public String eliminarCuenta(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        Cuenta cuenta = requireOwnedCuenta(id, user);
        if (cuenta == null) {
            redirectAttributes.addFlashAttribute("error", "Cuenta no encontrada.");
            return "redirect:/cuentas";
        }
        boolean eliminada = encryptedCuentaService.eliminarSiEstaVacia(cuenta);
        if (eliminada) {
            redirectAttributes.addFlashAttribute("success", "Cuenta eliminada correctamente.");
        } else {
            redirectAttributes.addFlashAttribute("error", "No se puede eliminar una cuenta con movimientos o transferencias. Desactívala en su lugar.");
        }
        return "redirect:/cuentas";
    }

    @PostMapping("/cuentas/transferir")
    public String transferir(@RequestParam Long cuentaOrigenId,
                             @RequestParam Long cuentaDestinoId,
                             @RequestParam String importe,
                             @RequestParam String fecha,
                             @RequestParam(required = false) String descripcion,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        Cuenta origen = requireOwnedCuenta(cuentaOrigenId, user);
        Cuenta destino = requireOwnedCuenta(cuentaDestinoId, user);
        if (origen == null || destino == null) {
            redirectAttributes.addFlashAttribute("error", "Cuenta de origen o destino no válida.");
            return "redirect:/movimientos";
        }
        try {
            double importeParsed = parseImporte(importe);
            LocalDate fechaParsed = LocalDate.parse(fecha);
            encryptedTransferenciaService.crearTransferencia(user, origen, destino, importeParsed, fechaParsed, descripcion);
            redirectAttributes.addFlashAttribute("success", "Transferencia realizada correctamente.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/movimientos";
    }

    @PostMapping("/movimientos/transferencias/delete/{id}")
    public String eliminarTransferencia(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        var transferencia = encryptedTransferenciaService.getById(id);
        if (transferencia == null || !transferencia.getUser().getId().equals(user.getId())) {
            redirectAttributes.addFlashAttribute("error", "Transferencia no encontrada.");
            return "redirect:/movimientos";
        }
        encryptedTransferenciaService.eliminar(transferencia);
        return "redirect:/movimientos";
    }

    /**
     * Resuelve una cuenta por id y verifica que pertenece al usuario de sesión.
     * Nunca se confía en el id recibido del formulario sin esta comprobación.
     */
    private Cuenta requireOwnedCuenta(Long id, User user) {
        Cuenta cuenta = encryptedCuentaService.getCuentaById(id);
        if (cuenta == null || cuenta.getUser() == null || !cuenta.getUser().getId().equals(user.getId())) {
            return null;
        }
        return cuenta;
    }

    private TipoCuenta parseTipo(String tipo) {
        try {
            return TipoCuenta.valueOf(tipo);
        } catch (Exception e) {
            throw new IllegalArgumentException("Tipo de cuenta inválido");
        }
    }

    private double parseImporte(String valor) {
        if (valor == null || valor.isBlank()) {
            return 0.0;
        }
        return Double.parseDouble(valor.replace(",", "."));
    }

    /**
     * Últimos 4 dígitos de la cuenta: campo opcional, solo para distinguir cuentas con nombres
     * parecidos. Si viene vacío se guarda como null; si no son exactamente 4 dígitos, se rechaza.
     */
    private String parseUltimosDigitos(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpio = valor.trim();
        if (!limpio.matches("\\d{4}")) {
            throw new IllegalArgumentException("Los últimos dígitos deben ser exactamente 4 números.");
        }
        return limpio;
    }
}
