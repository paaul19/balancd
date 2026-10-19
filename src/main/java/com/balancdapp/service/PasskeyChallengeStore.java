package com.balancdapp.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarda las opciones de passkey (con el reto aleatorio) de la API para apps móviles, que no tienen
 * sesión web: se entrega un requestId opaco y las opciones se consumen una sola vez, con caducidad.
 * En memoria: válido para un único proceso (el despliegue actual); con varias réplicas habría que
 * moverlo a una tabla o a Redis.
 */
@Component
public class PasskeyChallengeStore {

    private static final long TTL_MILLIS = 5 * 60_000L;
    private static final int PURGE_THRESHOLD = 1_000;

    public record Entry(String requestJson, Long userId) {}

    private record Stored(Entry entry, long expiresAt) {}

    private final Map<String, Stored> items = new ConcurrentHashMap<>();

    /** @param userId dueño esperado (registro); null en el login, donde aún no se sabe quién es. */
    public String put(String requestJson, Long userId) {
        if (items.size() > PURGE_THRESHOLD) {
            long now = System.currentTimeMillis();
            items.values().removeIf(s -> s.expiresAt() < now);
        }
        String id = UUID.randomUUID().toString();
        items.put(id, new Stored(new Entry(requestJson, userId), System.currentTimeMillis() + TTL_MILLIS));
        return id;
    }

    /** Devuelve y elimina las opciones; vacío si no existen o han caducado. */
    public Entry take(String requestId) {
        if (requestId == null) return null;
        Stored s = items.remove(requestId);
        return (s == null || s.expiresAt() < System.currentTimeMillis()) ? null : s.entry();
    }
}
