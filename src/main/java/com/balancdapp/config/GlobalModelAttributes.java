package com.balancdapp.config;

import com.balancdapp.model.User;
import com.balancdapp.service.EncryptedCuentaService;
import com.balancdapp.util.MoneyFormatter;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Collections;
import java.util.List;

/**
 * Añade "moneyFormatter" al modelo de TODAS las vistas automáticamente, sin tener que
 * inyectarlo a mano en cada controlador. Así cualquier plantilla puede usar
 * ${moneyFormatter.format(importe)} y siempre refleja la divisa preferida del usuario en sesión
 * (o EUR por defecto si no hay sesión, p. ej. en /login o la landing).
 */
@ControllerAdvice
public class GlobalModelAttributes {

    @Autowired
    private EncryptedCuentaService encryptedCuentaService;

    /**
     * Cuentas activas del usuario, disponibles en TODAS las páginas (no solo en las que ya las
     * pasaban a mano) para que el footer sepa si el usuario tiene cuentas antes de abrir el menú
     * de "Añadir". Sin esto, el botón "+" del footer en páginas como /ajustes/categorias no veía
     * ninguna cuenta (window.cuentasUsuario quedaba undefined) y decía "crea una cuenta primero"
     * aunque el usuario ya tuviera una.
     */
    @ModelAttribute("cuentasActivasGlobal")
    public List<EncryptedCuentaService.CuentaDTO> cuentasActivasGlobal(HttpSession session) {
        try {
            User user = (User) session.getAttribute("user");
            return user != null ? encryptedCuentaService.getCuentasActivasByUser(user) : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @ModelAttribute("moneyFormatter")
    public MoneyFormatter moneyFormatter(HttpSession session) {
        try {
            User user = (User) session.getAttribute("user");
            return new MoneyFormatter(user != null ? user.getMoneda() : "EUR");
        } catch (Exception e) {
            return new MoneyFormatter("EUR");
        }
    }
}
