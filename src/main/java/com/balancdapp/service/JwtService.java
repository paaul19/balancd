package com.balancdapp.service;

import com.balancdapp.model.User;
import com.balancdapp.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.security.Key;
import java.time.LocalDate;
import java.time.ZoneId;
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

    @Autowired
    private UserRepository userRepository;

    private Key key;
    private static final long EXPIRATION_MS = 7 * 24 * 60 * 60 * 1000; // 7 días
    public static final String CLAIM_TIPO = "tipo";
    public static final String TIPO_API = "api";
    public static final String CLAIM_VERSION = "v";
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

    /** Token de API generado desde Ajustes: igual que el del login, pero dura 1 año y lleva tipo=api y v. */
    public record ApiToken(String token, LocalDate caduca) {}

    public ApiToken generateApiToken(User user, int version) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("username", user.getUsername());
        claims.put(CLAIM_TIPO, TIPO_API);
        claims.put(CLAIM_VERSION, version);
        Date now = new Date();
        Date expiry = Date.from(now.toInstant().atZone(ZoneId.systemDefault()).plusYears(1).toInstant());
        String token = Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        return new ApiToken(token, expiry.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }

    public static boolean isApiToken(Claims claims) {
        return claims != null && TIPO_API.equals(claims.get(CLAIM_TIPO));
    }

    public Claims validateToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            // Tokens de API: solo valen mientras su "v" coincida con la api_token_version actual
            // del usuario (DELETE /api/ajustes/token la incrementa y los revoca todos). Se comprueba
            // aquí para que aplique en todos los controladores que validan JWT.
            if (isApiToken(claims)) {
                Object v = claims.get(CLAIM_VERSION);
                Object id = claims.get("id");
                if (!(v instanceof Number) || !(id instanceof Number)) return null;
                Integer actual = userRepository.findApiTokenVersionById(((Number) id).longValue()).orElse(null);
                if (actual == null || actual != ((Number) v).intValue()) return null;
            }
            return claims;
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