package com.balancdapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @org.springframework.beans.factory.annotation.Autowired
    private BanCheckFilter banCheckFilter;

    /**
     * Orígenes desde los que un navegador puede hacer peticiones con credenciales (cookie de
     * sesión) a las rutas web. Configurable por entorno; por defecto solo el dominio de
     * producción y localhost (para desarrollo). NUNCA "*": con allowCredentials=true eso permite
     * a cualquier sitio leer respuestas autenticadas del usuario (ver hallazgo C3 de la auditoría).
     */
    @Value("${app.cors.allowed-origins:https://balancd.es,http://localhost:8081}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/api/login", "/api/register", "/api/verify").permitAll()
                        .requestMatchers("/static/**", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/api/tutorial/**").authenticated()
                        .anyRequest().permitAll()
                )
                // La API (/api/**) se autentica con JWT por cabecera Authorization, no con
                // cookies: no es susceptible a CSRF y queda excluida. Todo lo demás (rutas web,
                // autenticadas por JSESSIONID) SÍ requiere token CSRF (ver hallazgo C4).
                // CookieCsrfTokenRepository con HttpOnly=false permite que el JavaScript de la
                // propia app lea el token de la cookie XSRF-TOKEN para inyectarlo en formularios
                // construidos dinámicamente y en peticiones fetch() (ver csrf.js).
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Spring Security 6 aplica por defecto un handler que ofusca (XOR) el
                        // valor del token por protección BREACH, pensado para cuando el token se
                        // renderiza en el HTML de cada página. Con CookieCsrfTokenRepository (el
                        // patrón "cookie legible por JS", como Angular/csrf.js) hace falta el
                        // handler simple para que el valor que el JS lee de la cookie coincida
                        // exactamente con el que el filtro espera recibir de vuelta.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/**")
                )
                // Spring Security resuelve el CsrfToken de forma perezosa: si nada lo lee
                // explícitamente durante la petición, la cookie XSRF-TOKEN nunca llega a
                // escribirse en la respuesta. Como esta app no renderiza ${_csrf} en las
                // plantillas Thymeleaf (csrf.js lee la cookie en su lugar), hace falta forzar
                // esa lectura en cada petición - es el propio patrón que documenta la guía de
                // Spring Security para SPA/JS con CookieCsrfTokenRepository.
                .addFilterAfter(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                            throws ServletException, IOException {
                        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                        if (csrfToken != null) {
                            csrfToken.getToken();
                        }
                        filterChain.doFilter(request, response);
                    }
                }, BasicAuthenticationFilter.class)
                .addFilterAfter(banCheckFilter, BasicAuthenticationFilter.class)
                .headers(headers -> headers
                        // Sustituye al frameOptions().disable() global anterior (motivado solo por
                        // la consola H2, que ahora está desactivada por completo - ver C5/H1).
                        .frameOptions(frame -> frame.sameOrigin())
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // HSTS: Spring Security solo añade esta cabecera en respuestas servidas
                        // sobre HTTPS (request.isSecure()); en local sobre HTTP no se observará,
                        // es el comportamiento esperado - se activa sola en producción.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                // La app usa manejadores onclick="" inline y bloques <style> inline
                                // en casi todos los fragments (header/footer/theme-switcher...);
                                // eliminar 'unsafe-inline' requeriría un refactor mucho mayor
                                // (mover todo a listeners + nonces) fuera del alcance de este arreglo.
                                "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdn.jsdelivr.net; " +
                                "font-src 'self' https://fonts.gstatic.com; " +
                                "img-src 'self' data:; " +
                                "connect-src 'self'; " +
                                "object-src 'none'; " +
                                "base-uri 'self'; " +
                                "frame-ancestors 'self'"
                        ))
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Content-Type", "Authorization", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
