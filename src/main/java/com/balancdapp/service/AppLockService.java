package com.balancdapp.service;

import com.balancdapp.model.User;
import com.balancdapp.repository.PasskeyCredentialRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Bloqueo de la app con passkey al abrirla.
 *
 * La sesión web dura 7 días, así que al reabrir la app el usuario seguiría dentro sin hacer
 * nada. Para quien tiene al menos una passkey vinculada, la sesión pasa a estar "bloqueada" (y
 * hay que verificar la passkey en /desbloquear antes de ver ningún dato) cuando:
 *   - Se ha cerrado el navegador / la PWA: el desbloqueo vive en una cookie de sesión (sin
 *     Max-Age) que el navegador descarta al cerrarse, mientras que JSESSIONID sí persiste.
 *   - Lleva más de {@code app.lock.idle-minutes} sin peticiones (p. ej. la app se quedó en
 *     segundo plano en el móvil y se vuelve a ella).
 * La cookie solo contiene un token aleatorio que debe coincidir con el guardado en la sesión.
 */
@Service
public class AppLockService {

    public static final String COOKIE_NAME = "BALANCD_UNLOCK";
    private static final String SESSION_TOKEN = "appLockToken";
    private static final String SESSION_LAST_SEEN = "appLockLastSeen";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PasskeyCredentialRepository passkeyRepository;
    private final long idleMillis;
    private final int idleMinutes;
    private final boolean secureCookie;

    public AppLockService(PasskeyCredentialRepository passkeyRepository,
                          @Value("${app.lock.idle-minutes:5}") int idleMinutes,
                          @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie) {
        this.passkeyRepository = passkeyRepository;
        this.idleMinutes = idleMinutes;
        this.idleMillis = idleMinutes * 60_000L;
        this.secureCookie = secureCookie;
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    /** El bloqueo solo aplica a quien puede desbloquear: usuarios con alguna passkey. */
    public boolean isEnabledFor(User user) {
        return user != null && passkeyRepository.existsByUserId(user.getId());
    }

    public boolean isUnlocked(HttpServletRequest request, HttpSession session) {
        String token = (String) session.getAttribute(SESSION_TOKEN);
        Long lastSeen = (Long) session.getAttribute(SESSION_LAST_SEEN);
        if (token == null || lastSeen == null) return false;
        if (System.currentTimeMillis() - lastSeen > idleMillis) return false;
        return token.equals(readCookie(request));
    }

    /** Registra actividad: alarga el periodo de desbloqueo. */
    public void touch(HttpSession session) {
        session.setAttribute(SESSION_LAST_SEEN, System.currentTimeMillis());
    }

    /** Marca la sesión como desbloqueada tras una autenticación (contraseña o passkey). */
    public void markUnlocked(HttpSession session, HttpServletResponse response) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_TOKEN, token);
        touch(session);
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
                .path("/")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .build(); // sin maxAge: cookie de sesión del navegador
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
