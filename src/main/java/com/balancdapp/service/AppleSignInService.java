package com.balancdapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Verifica el "identity token" de Sign in with Apple: firma RS256 con las claves públicas de Apple (JWKS),
 * emisor, audiencia (bundle id de la app), caducidad y nonce (anti-replay).
 */
@Service
public class AppleSignInService {
    private static final String ISSUER = "https://appleid.apple.com";
    private static final String KEYS_URL = "https://appleid.apple.com/auth/keys";
    private static final long KEYS_TTL_MS = 60 * 60 * 1000L;

    /** Bundle id de la app iOS (audiencia esperada del token). Sobrescribible con APP_APPLE_CLIENT_ID. */
    @Value("${app.apple.client-id:com.balancdapp.ios}")
    private String clientId;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile Map<String, PublicKey> keys = Map.of();
    private volatile long keysFetchedAt = 0;

    public record AppleIdentity(String sub, String email, boolean emailVerified) {}

    public AppleIdentity verify(String identityToken, String rawNonce) {
        if (identityToken == null || identityToken.isBlank() || rawNonce == null || rawNonce.isBlank()) {
            throw new IllegalArgumentException("Faltan datos del inicio de sesión con Apple");
        }
        try {
            String[] parts = identityToken.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException("Token de Apple inválido");
            JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            PublicKey key = keyFor(header.path("kid").asText());

            Claims c = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .requireIssuer(ISSUER)
                    .requireAudience(clientId)
                    .setAllowedClockSkewSeconds(60)
                    .build()
                    .parseClaimsJws(identityToken)
                    .getBody();

            String esperado = sha256Hex(rawNonce);
            if (!esperado.equals(c.get("nonce", String.class))) {
                throw new IllegalArgumentException("Inicio de sesión con Apple no válido (nonce)");
            }
            Object ev = c.get("email_verified");
            boolean verificado = ev instanceof Boolean b ? b : "true".equals(String.valueOf(ev));
            return new AppleIdentity(c.getSubject(), c.get("email", String.class), verificado);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo verificar el inicio de sesión con Apple");
        }
    }

    private PublicKey keyFor(String kid) throws Exception {
        long now = System.currentTimeMillis();
        PublicKey k = keys.get(kid);
        if (k == null || now - keysFetchedAt > KEYS_TTL_MS) {
            refreshKeys();
            k = keys.get(kid);
        }
        if (k == null) throw new IllegalArgumentException("Clave de Apple desconocida");
        return k;
    }

    private synchronized void refreshKeys() throws Exception {
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create(KEYS_URL)).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("No se pudieron obtener las claves de Apple");
        Map<String, PublicKey> nuevas = new HashMap<>();
        KeyFactory kf = KeyFactory.getInstance("RSA");
        for (JsonNode jwk : mapper.readTree(r.body()).path("keys")) {
            if (!"RSA".equals(jwk.path("kty").asText())) continue;
            BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("n").asText()));
            BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("e").asText()));
            nuevas.put(jwk.path("kid").asText(), kf.generatePublic(new RSAPublicKeySpec(n, e)));
        }
        keys = nuevas;
        keysFetchedAt = System.currentTimeMillis();
    }

    private static String sha256Hex(String s) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : h) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
