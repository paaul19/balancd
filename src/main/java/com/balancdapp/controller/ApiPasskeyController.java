package com.balancdapp.controller;

import com.balancdapp.model.PasskeyCredential;
import com.balancdapp.model.User;
import com.balancdapp.service.JwtService;
import com.balancdapp.service.PasskeyChallengeStore;
import com.balancdapp.service.PasskeyService;
import com.balancdapp.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Passkeys para apps móviles (Android): mismo servicio que la web pero con JWT en vez de sesión y con un
 * requestId opaco en lugar de guardar el reto en la sesión. Las opciones se devuelven como
 * {"requestId": "...", "publicKey": {...}}, que es lo que el Administrador de credenciales de Android espera.
 */
@RestController
@RequestMapping("/api")
public class ApiPasskeyController {

    private static final Logger log = LoggerFactory.getLogger(ApiPasskeyController.class);

    private final PasskeyService passkeyService;
    private final PasskeyChallengeStore challenges;
    private final JwtService jwtService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public ApiPasskeyController(PasskeyService passkeyService, PasskeyChallengeStore challenges, JwtService jwtService,
                                UserService userService, ObjectMapper objectMapper) {
        this.passkeyService = passkeyService;
        this.challenges = challenges;
        this.jwtService = jwtService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    private User auth(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ")) return null;
        Claims c = jwtService.validateToken(h.substring(7));
        if (c == null) return null;
        User u = userService.getUserById(((Number) c.get("id")).longValue()).orElse(null);
        return (u != null && u.isBaneado()) ? null : u;
    }

    private static ResponseEntity<?> error(HttpStatus status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }

    /** {"publicKey": {...}} de la librería -> {"requestId": id, "publicKey": {...}}. */
    private ResponseEntity<?> opciones(String optionsJson, String requestId) throws Exception {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("requestId", requestId);
        out.set("publicKey", objectMapper.readTree(optionsJson).path("publicKey"));
        return ResponseEntity.ok(out);
    }

    // ---------------------------------------------------------------- Login (público)

    @PostMapping("/passkey/login/opciones")
    public ResponseEntity<?> opcionesLogin() throws Exception {
        AssertionRequest request = passkeyService.startAssertion();
        return opciones(request.toCredentialsGetJson(), challenges.put(request.toJson(), null));
    }

    @PostMapping("/passkey/login")
    public ResponseEntity<?> login(@RequestBody JsonNode body) throws Exception {
        PasskeyChallengeStore.Entry entry = challenges.take(body.path("requestId").asText(null));
        if (entry == null) return error(HttpStatus.BAD_REQUEST, "La solicitud ha caducado. Vuelve a intentarlo.");
        User user;
        try {
            user = passkeyService.finishAssertion(entry.requestJson(), objectMapper.writeValueAsString(body.path("credential"))).orElse(null);
        } catch (Exception e) {
            log.warn("Respuesta de passkey (API) inválida: {}", e.getMessage());
            user = null;
        }
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "No se pudo verificar la passkey.");
        // Mismas comprobaciones que el login con contraseña.
        if (!user.isVerified()) return error(HttpStatus.FORBIDDEN, "Debes verificar tu correo antes de iniciar sesión");
        if (user.isBaneado()) return error(HttpStatus.FORBIDDEN, "Tu cuenta ha sido suspendida");
        Map<String, Object> response = new HashMap<>();
        response.put("token", jwtService.generateToken(user));
        response.put("user", Map.of("id", user.getId(), "username", user.getUsername()));
        return ResponseEntity.ok(response);
    }

    // ---------------------------------------------------------------- Gestión (con JWT)

    @GetMapping("/passkeys")
    public ResponseEntity<?> listar(HttpServletRequest req) {
        User user = auth(req);
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "Token no proporcionado o inválido");
        List<Map<String, Object>> out = passkeyService.listForUser(user.getId()).stream().map(pk -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", pk.getId());
            m.put("nombre", pk.getNombre());
            m.put("createdAt", pk.getCreatedAt() == null ? null : pk.getCreatedAt().toString());
            m.put("lastUsedAt", pk.getLastUsedAt() == null ? null : pk.getLastUsedAt().toString());
            return m;
        }).toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping("/passkeys/opciones")
    public ResponseEntity<?> opcionesRegistro(HttpServletRequest req) throws Exception {
        User user = auth(req);
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "Token no proporcionado o inválido");
        PublicKeyCredentialCreationOptions options = passkeyService.startRegistration(user);
        return opciones(options.toCredentialsCreateJson(), challenges.put(options.toJson(), user.getId()));
    }

    @PostMapping("/passkeys")
    public ResponseEntity<?> registrar(@RequestBody JsonNode body, HttpServletRequest req) throws Exception {
        User user = auth(req);
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "Token no proporcionado o inválido");
        PasskeyChallengeStore.Entry entry = challenges.take(body.path("requestId").asText(null));
        if (entry == null || !user.getId().equals(entry.userId())) {
            return error(HttpStatus.BAD_REQUEST, "No hay ningún registro de passkey en curso.");
        }
        try {
            PasskeyCredential saved = passkeyService.finishRegistration(user, entry.requestJson(),
                    objectMapper.writeValueAsString(body.path("credential")), body.path("nombre").asText(""));
            return ResponseEntity.ok(Map.of("id", saved.getId(), "nombre", saved.getNombre()));
        } catch (Exception e) {
            log.warn("Registro de passkey (API) fallido para usuario {}: {}", user.getId(), e.getMessage());
            return error(HttpStatus.BAD_REQUEST, "No se pudo vincular la passkey.");
        }
    }

    @PostMapping("/passkeys/{id}/eliminar")
    public ResponseEntity<?> eliminar(@PathVariable Long id, HttpServletRequest req) {
        User user = auth(req);
        if (user == null) return error(HttpStatus.UNAUTHORIZED, "Token no proporcionado o inválido");
        if (!passkeyService.delete(user.getId(), id)) return error(HttpStatus.BAD_REQUEST, "No se encontró la passkey.");
        return ResponseEntity.ok(Map.of("success", true));
    }
}
