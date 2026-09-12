package com.balancdapp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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
            new Rule("/auth/register", "POST", 5, 15 * 60 * 1000),
            new Rule("/forgot-password", "POST", 5, 15 * 60 * 1000),
            new Rule("/verify", "GET", 20, 5 * 60 * 1000),
            new Rule("/api/login", "POST", 10, 5 * 60 * 1000),
            new Rule("/api/register", "POST", 5, 15 * 60 * 1000),
    };

    private final Map<String, ConcurrentLinkedDeque<Long>> hits = new ConcurrentHashMap<>();

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
        // X-Forwarded-For: confiamos en ella porque el despliegue real siempre va detrás de un
        // reverse proxy (ver docker-compose.yml); si no viene, se usa la IP directa de la conexión.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
