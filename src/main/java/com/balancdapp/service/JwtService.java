package com.balancdapp.service;

import com.balancdapp.model.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {
    // Sin valor por defecto a propósito: si APP_JWT_SECRET no está configurado, la
    // aplicación debe fallar al arrancar en lugar de firmar tokens con un secreto
    // conocido/predecible (ver hallazgo C2 de la auditoría de seguridad).
    @Value("${app.jwt.secret}")
    private String secret;

    private Key key;
    private static final long EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000; // 7 días
    private static final int MIN_SECRET_BYTES = 32; // 256 bits, mínimo recomendado para HS256

    @PostConstruct
    public void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "app.jwt.secret (variable de entorno APP_JWT_SECRET) no está configurado. " +
                "La aplicación no puede arrancar sin un secreto JWT explícito.");
        }
        byte[] keyBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "app.jwt.secret es demasiado corto (" + keyBytes.length + " bytes); " +
                "se requieren al menos " + MIN_SECRET_BYTES + " bytes (256 bits) para HS256.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("username", user.getUsername());
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION_MS);
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims validateToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException | IllegalArgumentException e) {
            // JwtException cubre TODAS las causas de invalidez de jjwt (firma incorrecta,
            // expirado, malformado, algoritmo no soportado...) - antes solo se capturaban
            // algunas subclases, así que un token con firma inválida (p. ej. uno forjado con
            // un secreto distinto al configurado) no quedaba cubierto y provocaba un 500 en
            // vez de un 401 limpio. Encontrado al verificar el hallazgo C2 tras el arreglo.
            return null;
        }
    }
} 