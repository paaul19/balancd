package com.balancdapp.controller;

import com.balancdapp.model.PasskeyCredential;
import com.balancdapp.config.AppLockFilter;
import com.balancdapp.model.User;
import com.balancdapp.service.AppLockService;
import com.balancdapp.service.PasskeyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * Endpoints web (sesión + CSRF por cabecera X-XSRF-TOKEN, ver csrf.js) para passkeys:
 *  - Vincular/eliminar desde /perfil (requiere sesión iniciada).
 *  - Iniciar sesión desde /login.
 *  - Desbloquear la app desde /desbloquear (ver AppLockService).
 * Las opciones que genera el servidor (con el reto aleatorio) se guardan en la sesión y se
 * consumen una única vez en el paso "finish", para que un reto no pueda reutilizarse.
 */
@Controller
public class PasskeyController {

    private static final Logger log = LoggerFactory.getLogger(PasskeyController.class);
    private static final String SESSION_REGISTRATION = "passkeyRegistrationRequest";
    private static final String SESSION_ASSERTION = "passkeyAssertionRequest";
    private static final String SESSION_UNLOCK = "passkeyUnlockRequest";

    private final PasskeyService passkeyService;
    private final AppLockService appLockService;
    private final ObjectMapper objectMapper;

    public PasskeyController(PasskeyService passkeyService, AppLockService appLockService, ObjectMapper objectMapper) {
        this.passkeyService = passkeyService;
        this.appLockService = appLockService;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- Vincular (perfil)

    @PostMapping(value = "/perfil/passkeys/opciones", produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> opcionesRegistro(HttpSession session) throws Exception {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401).body(error("Sesión caducada. Inicia sesión de nuevo."));
        }
        PublicKeyCredentialCreationOptions options = passkeyService.startRegistration(user);
        session.setAttribute(SESSION_REGISTRATION, options.toJson());
        return ResponseEntity.ok(options.toCredentialsCreateJson());
    }

    @PostMapping(value = "/perfil/passkeys", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> registrar(@RequestBody String body, HttpSession session,
                                            HttpServletResponse response) throws Exception {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401).body(error("Sesión caducada. Inicia sesión de nuevo."));
        }
        String requestJson = (String) session.getAttribute(SESSION_REGISTRATION);
        session.removeAttribute(SESSION_REGISTRATION);
        if (requestJson == null) {
            return ResponseEntity.badRequest().body(error("No hay ningún registro de passkey en curso."));
        }
        JsonNode root = objectMapper.readTree(body);
        String nombre = root.path("nombre").asText("");
        String credential = objectMapper.writeValueAsString(root.path("credential"));
        try {
            PasskeyCredential saved = passkeyService.finishRegistration(user, requestJson, credential, nombre);
            // Acaba de verificarse con Face ID / Touch ID: que vincular la primera passkey no
            // bloquee la app en la siguiente petición.
            appLockService.markUnlocked(session, response);
            return ResponseEntity.ok(objectMapper.writeValueAsString(Map.of("id", saved.getId(), "nombre", saved.getNombre())));
        } catch (Exception e) {
            log.warn("Registro de passkey fallido para usuario {}: {}", user.getId(), e.getMessage());
            return ResponseEntity.badRequest().body(error("No se pudo vincular la passkey."));
        }
    }

    @PostMapping("/perfil/passkeys/{id}/eliminar")
    public String eliminar(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        if (passkeyService.delete(user.getId(), id)) {
            redirectAttributes.addFlashAttribute("success",
                    "Passkey eliminada. Bórrala también del llavero de tu dispositivo para que deje de aparecer.");
        } else {
            redirectAttributes.addFlashAttribute("error", "No se encontró la passkey.");
        }
        return "redirect:/perfil";
    }

    // ---------------------------------------------------------------- Login

    @PostMapping(value = "/login/passkey/opciones", produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> opcionesLogin(HttpSession session) throws Exception {
        AssertionRequest request = passkeyService.startAssertion();
        session.setAttribute(SESSION_ASSERTION, request.toJson());
        return ResponseEntity.ok(request.toCredentialsGetJson());
    }

    @PostMapping(value = "/login/passkey", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> login(@RequestBody String body, HttpServletRequest httpRequest,
                                        HttpServletResponse httpResponse) throws Exception {
        HttpSession session = httpRequest.getSession();
        String requestJson = (String) session.getAttribute(SESSION_ASSERTION);
        session.removeAttribute(SESSION_ASSERTION);
        if (requestJson == null) {
            return ResponseEntity.badRequest().body(error("La solicitud ha caducado. Vuelve a intentarlo."));
        }

        User user;
        try {
            user = passkeyService.finishAssertion(requestJson, body).orElse(null);
        } catch (Exception e) {
            log.warn("Respuesta de passkey inválida: {}", e.getMessage());
            user = null;
        }
        if (user == null) {
            return ResponseEntity.status(401).body(error("No se pudo verificar la passkey."));
        }
        // Mismas comprobaciones que el login con contraseña (AuthController.login).
        if (!user.isVerified()) {
            return ResponseEntity.status(403).body(error("Debes verificar tu correo antes de iniciar sesión."));
        }
        if (user.isBaneado()) {
            return ResponseEntity.status(403).body(error("Tu cuenta ha sido suspendida."));
        }
        // Nuevo id de sesión al autenticarse (evita fijación de sesión).
        httpRequest.changeSessionId();
        session.setAttribute("user", user);
        appLockService.markUnlocked(session, httpResponse);
        return ResponseEntity.ok(objectMapper.writeValueAsString(Map.of("redirect", "/movimientos")));
    }

    // ---------------------------------------------------------------- Desbloquear la app

    @GetMapping("/desbloquear")
    public String desbloquearForm(HttpServletRequest request, HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        if (!appLockService.isEnabledFor(user) || appLockService.isUnlocked(request, session)) {
            return "redirect:" + consumeNext(session);
        }
        model.addAttribute("username", user.getUsername());
        return "auth/desbloquear";
    }

    @PostMapping(value = "/desbloquear/opciones", produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> opcionesDesbloqueo(HttpSession session) throws Exception {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(401).body(error("Sesión caducada. Inicia sesión de nuevo."));
        }
        AssertionRequest request = passkeyService.startAssertion(user);
        session.setAttribute(SESSION_UNLOCK, request.toJson());
        return ResponseEntity.ok(request.toCredentialsGetJson());
    }

    @PostMapping(value = "/desbloquear", consumes = "application/json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<String> desbloquear(@RequestBody String body, HttpSession session,
                                              HttpServletResponse response) throws Exception {
        User sessionUser = (User) session.getAttribute("user");
        if (sessionUser == null) {
            return ResponseEntity.status(401).body(error("Sesión caducada. Inicia sesión de nuevo."));
        }
        String requestJson = (String) session.getAttribute(SESSION_UNLOCK);
        session.removeAttribute(SESSION_UNLOCK);
        if (requestJson == null) {
            return ResponseEntity.badRequest().body(error("La solicitud ha caducado. Vuelve a intentarlo."));
        }

        User user;
        try {
            user = passkeyService.finishAssertion(requestJson, body).orElse(null);
        } catch (Exception e) {
            log.warn("Respuesta de passkey inválida al desbloquear: {}", e.getMessage());
            user = null;
        }
        // La passkey tiene que ser del mismo usuario de la sesión, no de cualquier cuenta.
        if (user == null || !user.getId().equals(sessionUser.getId())) {
            return ResponseEntity.status(401).body(error("No se pudo verificar la passkey."));
        }
        appLockService.markUnlocked(session, response);
        return ResponseEntity.ok(objectMapper.writeValueAsString(Map.of("redirect", consumeNext(session))));
    }

    /** Página a la que se iba antes del bloqueo (solo rutas internas guardadas por AppLockFilter). */
    private static String consumeNext(HttpSession session) {
        Object next = session.getAttribute(AppLockFilter.SESSION_NEXT);
        session.removeAttribute(AppLockFilter.SESSION_NEXT);
        if (next instanceof String s && s.startsWith("/") && !s.startsWith("//")) {
            return s;
        }
        return "/movimientos";
    }

    private String error(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (Exception e) {
            return "{\"error\":\"Error\"}";
        }
    }
}
