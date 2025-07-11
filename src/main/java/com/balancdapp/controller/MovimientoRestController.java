package com.balancdapp.controller;

import com.balancdapp.model.User;
import com.balancdapp.service.EncryptedMovimientoService;
import com.balancdapp.service.JwtService;
import com.balancdapp.service.UserService;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/movimientos")
public class MovimientoRestController {
    @Autowired
    private EncryptedMovimientoService encryptedMovimientoService;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserService userService;

    @PostMapping
    public ResponseEntity<?> addMovimiento(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token no proporcionado"));
        }
        String token = authHeader.substring(7);
        Claims claims = jwtService.validateToken(token);
        if (claims == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token inválido o expirado"));
        }
        Long userId = ((Number) claims.get("id")).longValue();
        Optional<User> userOpt = userService.getUserById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Usuario no encontrado"));
        }
        User user = userOpt.get();
        try {
            double cantidad = Double.parseDouble(payload.get("cantidad").toString());
            boolean ingreso = Boolean.parseBoolean(payload.get("ingreso").toString());
            String asunto = payload.get("asunto").toString();
            String fechaStr = payload.get("fecha").toString(); // formato: yyyy-MM-dd
            LocalDate fecha = LocalDate.parse(fechaStr, DateTimeFormatter.ISO_DATE);
            int mesAsignado = Integer.parseInt(payload.get("mesAsignado").toString());
            int anioAsignado = Integer.parseInt(payload.get("anioAsignado").toString());
            encryptedMovimientoService.createMovimiento(user, cantidad, ingreso, asunto, fecha, mesAsignado, anioAsignado, null);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }
} 