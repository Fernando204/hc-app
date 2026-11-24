package com.horsecontrol.eloquestre.config;

import com.horsecontrol.eloquestre.services.internal.Logger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Registra cada requisição no console: método, caminho, status e tempo.
 * Nunca registra o corpo (tem senha) nem o header Authorization (tem o token).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            String line = request.getMethod() + " " + request.getRequestURI()
                    + " → " + status + " (" + (System.currentTimeMillis() - start) + " ms)"
                    + " de " + request.getRemoteAddr();
            if (status >= 500) Logger.error(line);
            else if (status >= 400) Logger.warn(line);
            else Logger.info(line);
        }
    }

    /** Preflight de CORS não interessa no log. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }
}
