package com.balancdapp.config;

import com.balancdapp.model.User;
import com.balancdapp.service.AppLockService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Exige verificar la passkey al abrir la app (ver AppLockService). Con la sesión bloqueada,
 * las páginas redirigen a /desbloquear (recordando a dónde se iba) y las peticiones fetch()
 * reciben un 401 con la cabecera X-App-Locked, que app-lock.js convierte en esa misma redirección.
 */
@Component
public class AppLockFilter extends OncePerRequestFilter {

    public static final String SESSION_NEXT = "appLockNext";

    @Autowired
    private AppLockService appLockService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/api/") || path.startsWith("/desbloquear") || path.startsWith("/login")
                || path.equals("/logout") || path.equals("/error")
                || path.equals("/manifest.json") || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        User user = session != null ? (User) session.getAttribute("user") : null;
        if (user == null || !appLockService.isEnabledFor(user)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!appLockService.isUnlocked(request, session)) {
            String accept = request.getHeader("Accept");
            if ("GET".equals(request.getMethod()) && accept != null && accept.contains("text/html")) {
                String query = request.getQueryString();
                String path = request.getRequestURI().substring(request.getContextPath().length());
                session.setAttribute(SESSION_NEXT, query == null ? path : path + "?" + query);
                response.sendRedirect(request.getContextPath() + "/desbloquear");
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("X-App-Locked", "1");
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"La app está bloqueada.\",\"redirect\":\"/desbloquear\"}");
            }
            return;
        }

        appLockService.touch(session);
        // Para que las plantillas incluyan app-lock.js (pwa-meta.html).
        request.setAttribute("appLockActivo", true);
        request.setAttribute("appLockIdleMinutes", appLockService.getIdleMinutes());
        filterChain.doFilter(request, response);
    }
}
