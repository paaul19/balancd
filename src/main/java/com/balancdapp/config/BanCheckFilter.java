package com.balancdapp.config;

import com.balancdapp.model.User;
import com.balancdapp.repository.UserRepository;
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
 * Corta el acceso de inmediato cuando el panel de administración externo (puerto 9093) banea
 * una cuenta: la sesión web se reevalúa contra BD en cada petición (no solo en el login), así
 * que un usuario baneado con la sesión ya abierta pierde el acceso en la siguiente petición, sin
 * tener que esperar a que la sesión caduque o cierre sesión él mismo. El acceso por API (JWT) se
 * corta de la misma forma en MovimientoRestController.authenticate().
 */
@Component
public class BanCheckFilter extends OncePerRequestFilter {

    @Autowired
    private UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/api/") || path.equals("/manifest.json") || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            User sessionUser = (User) session.getAttribute("user");
            if (sessionUser != null) {
                User fresh = userRepository.findById(sessionUser.getId()).orElse(null);
                if (fresh == null || fresh.isBaneado()) {
                    session.invalidate();
                    response.sendRedirect(request.getContextPath() + "/login?banned=1");
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
