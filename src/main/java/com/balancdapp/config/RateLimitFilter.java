package com.balancdapp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Hallazgo M1: límite de peticiones sencillo, en memoria, para los endpoints de
 * autenticación/registro/recuperación (los que un atacante podría abusar para fuerza bruta,
 * credential stuffing o spam de emails). No usa ninguna librería externa: para un único
 * proceso/contenedor (el despliegue actual de balanc*d) un contador en memoria por IP es
 * suficiente y no añade una dependencia nueva al proyecto.
 *
 * Si en el futuro la app se despliega con varias réplicas detrás de un balanceador, este
 * límite pasaría a ser "por instancia" en vez de global - documentado como limitación
 * conocida en el informe de la auditoría.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String pathPrefix, String method, int maxRequests, long windowMillis) {}

    // Límites deliberadamente holgados para no bloquear el uso normal: un usuario que se
    // equivoca de contraseña varias veces seguidas no debería notarlo.
    private final Rule[] rules = new Rule[] {
            new Rule("/login", "POST", 10, 5 * 60 * 1000),
            new Rule("/login/passkey", "POST", 10, 5 * 60 * 1000),
            new Rule("/login/passkey/opciones", "POST", 30, 5 * 60 * 1000),
            new Rule("/auth/register", "POST", 5, 15 * 60 * 1000),
            new Rule("/forgot-password", "POST", 5, 15 * 60 * 1000),
            new Rule("/verify", "GET", 20, 5 * 60 * 1000),
            new Rule("/api/login", "POST", 10, 5 * 60 * 1000),
            new Rule("/api/register", "POST", 5, 15 * 60 * 1000),
            new Rule("/api/forgot-password", "POST", 5, 15 * 60 * 1000),
            new Rule("/api/auth/apple", "POST", 20, 5 * 60 * 1000),
    };

    private final Map<String, ConcurrentLinkedDeque<Long>> hits = new ConcurrentHashMap<>();

    /**
     * Cuántos proxies de confianza hay delante de la app (1 = un nginx/Caddy; 2 = p. ej. Cloudflare
     * + nginx). La IP del cliente es la entrada de X-Forwarded-For que añadió el proxy MÁS CERCANO,
     * contando desde la derecha: lo que el cliente escriba a la izquierda no cuenta.
     */
    @Value("${app.rate-limit.trusted-proxy-hops:1}")
    private int trustedProxyHops;

    private static final long MAX_WINDOW_MILLIS = 15 * 60 * 1000L;
    private static final int PURGE_THRESHOLD = 10_000;
    private volatile long lastPurge = 0;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Rule matched = null;
        for (Rule rule : rules) {
            if (request.getRequestURI().equals(rule.pathPrefix()) && request.getMethod().equalsIgnoreCase(rule.method())) {
                matched = rule;
                break;
            }
        }

        if (matched != null && isRateLimited(matched, request)) {
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Demasiadas peticiones. Inténtalo de nuevo en unos minutos.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(Rule rule, HttpServletRequest request) {
        String key = rule.pathPrefix() + "|" + clientIp(request);
        long now = System.currentTimeMillis();
        long windowStart = now - rule.windowMillis();
        purgeIfNeeded(now);

        ConcurrentLinkedDeque<Long> timestamps = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= rule.maxRequests()) {
                return true;
            }
            timestamps.addLast(now);
        }
        return false;
    }

    private String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        // Solo se mira la cabecera si la conexión llega desde un proxy interno (loopback/red
        // privada). Antes se usaba SIEMPRE el primer valor, que lo controla el cliente: enviando
        // una IP distinta en cada petición se evitaba el límite por completo.
        if (forwarded == null || forwarded.isBlank() || !isInternal(remote)) {
            return remote;
        }
        String[] partes = forwarded.split(",");
        int idx = partes.length - Math.max(1, trustedProxyHops);
        if (idx < 0) {
            return remote;
        }
        String candidata = partes[idx].trim();
        return candidata.isEmpty() ? remote : candidata;
    }

    private static boolean isInternal(String ip) {
        try {
            InetAddress a = InetAddress.getByName(ip); // ip viene de getRemoteAddr(): ya es literal, sin DNS
            if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress()) return true;
            byte[] b = a.getAddress();
            return b.length == 16 && (b[0] & 0xfe) == 0xfc; // IPv6 unique-local fc00::/7
        } catch (Exception e) {
            return false;
        }
    }

    /** Evita que el mapa crezca sin límite (p. ej. con muchas IPs distintas). */
    private void purgeIfNeeded(long now) {
        if (hits.size() < PURGE_THRESHOLD || now - lastPurge < 60_000) return;
        lastPurge = now;
        hits.entrySet().removeIf(e -> {
            Long last = e.getValue().peekLast();
            return last == null || last < now - MAX_WINDOW_MILLIS;
        });
    }
}
