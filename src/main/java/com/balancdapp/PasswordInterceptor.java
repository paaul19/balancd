package com.balancdapp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

public class PasswordInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        HttpSession session = request.getSession();
        // Permitir acceso a la pantalla de contraseña, login y recursos estáticos
        if (uri.startsWith("/acceso") || uri.startsWith("/login") || uri.startsWith("/css") || uri.startsWith("/js") || uri.startsWith("/images") || uri.startsWith("/resources") || uri.startsWith("/snippets") || uri.startsWith("/favicon")) {
            return true;
        }
        Boolean accesoPermitido = (Boolean) session.getAttribute("accesoPermitido");
        if (accesoPermitido != null && accesoPermitido) {
            return true;
        }
        response.sendRedirect("/acceso");
        return false;
    }
} 