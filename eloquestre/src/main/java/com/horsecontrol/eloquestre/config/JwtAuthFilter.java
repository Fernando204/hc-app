package com.horsecontrol.eloquestre.config;

import com.horsecontrol.eloquestre.services.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Exige "Authorization: Bearer <jwt>" em todas as rotas, exceto /auth/**, /ws, /files/** e /error.
 * O id do usuário fica no atributo da requisição {@link #USER_ID} (use @RequestAttribute).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String USER_ID = "userId";

    private static final java.util.Set<String> PUBLIC_AUTH = java.util.Set.of(
            "/auth/register", "/auth/login", "/auth/forgot-password", "/auth/resend-confirmation", "/auth/exchange");

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || PUBLIC_AUTH.contains(path) // /auth/logout e /auth/reset-password exigem token
                || path.equals("/ws") // autenticado no handshake (WebSocketAuthInterceptor)
                || path.startsWith("/files/") // arquivos enviados: públicos (nomes UUID)
                || path.startsWith("/webhooks/") // autenticados pelo próprio segredo (ex.: RevenueCat)
                || path.startsWith("/public/") // prévias das páginas de compartilhamento (share-web)
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String userId = header != null && header.startsWith("Bearer ")
                ? jwtService.userIdFrom(header.substring(7).trim()).orElse(null)
                : null;

        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":401,\"error\":\"Unauthorized\","
                    + "\"message\":\"not_authenticated\",\"path\":\"" + request.getRequestURI().replace("\"", "") + "\"}");
            return;
        }
        request.setAttribute(USER_ID, userId);
        chain.doFilter(request, response);
    }
}
