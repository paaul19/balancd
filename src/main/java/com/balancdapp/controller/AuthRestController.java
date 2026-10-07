package com.balancdapp.controller;

import com.balancdapp.model.User;
import com.balancdapp.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.balancdapp.service.JwtService;
import com.balancdapp.service.AppleSignInService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthRestController {
    @Autowired
    private UserService userService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AppleSignInService appleSignInService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        return userService.authenticateUser(username, password)
                .map(user -> {
                    if (!user.isVerified()) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Debes verificar tu correo antes de iniciar sesión"));
                    }
                    if (user.isBaneado()) {
                        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Tu cuenta ha sido suspendida"));
                    }
                    String token = jwtService.generateToken(user);
                    Map<String, Object> response = new HashMap<>();
                    response.put("token", token);
                    response.put("user", Map.of("id", user.getId(), "username", user.getUsername()));
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid username or password")));
    }

    /**
     * Sign in with Apple (app iOS). Recibe el identity token de Apple y el nonce en claro con el que se pidió;
     * responde igual que /api/login. Crea la cuenta (ya verificada) o la vincula si el correo verificado coincide.
     */
    @PostMapping("/auth/apple")
    public ResponseEntity<?> apple(@RequestBody Map<String, String> payload) {
        AppleSignInService.AppleIdentity identidad;
        try {
            identidad = appleSignInService.verify(payload.get("identityToken"), payload.get("nonce"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
        User user = userService.loginOrCreateWithApple(identidad.sub(), identidad.email(), identidad.emailVerified(), payload.get("fullName"));
        if (user.isBaneado()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Tu cuenta ha sido suspendida"));
        }
        Map<String, Object> response = new HashMap<>();
        response.put("token", jwtService.generateToken(user));
        response.put("user", Map.of("id", user.getId(), "username", user.getUsername()));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String password = payload.get("password");
        String email = payload.get("email");
        
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Username is required"));
        }
        if (password == null || password.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Password is required"));
        }
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Email is required"));
        }
        
        if (userService.getUserByUsername(username).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already in use"));
        }
        if (userService.getUserByEmail(email).isPresent()) {
            // Misma respuesta que un registro correcto: no se revela si el correo ya tiene cuenta.
            return ResponseEntity.ok(Map.of("success", true, "message", "Account created successfully. Please check your email to verify your account."));
        }
        
        try {
            User user = new User();
            user.setUsername(username);
            user.setPassword(password);
            user.setEmail(email);
            userService.registerUser(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Account created successfully. Please check your email to verify your account."));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/verify")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        boolean verified = userService.verifyUser(token);
        if (verified) {
            return ResponseEntity.ok(Map.of("success", true, "message", "Cuenta verificada exitosamente"));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("success", false, "error", "Token inválido o expirado"));
        }
    }
} 