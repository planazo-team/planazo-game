package com.planazo.minijuego.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Pone {@code x-served-by: game@<réplica>} en cada respuesta (ESC-04 y ESC-07),
 * igual que los servicios NestJS. En Railway la réplica es {@code RAILWAY_REPLICA_ID};
 * en Docker o en local, el hostname. El gateway lo lee en {@code /api/health/cluster}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ServidoPorFilter extends OncePerRequestFilter {

    static final String SERVIDO_POR = "game@" + idReplica(System.getenv());

    /** Recorta el id a 8 caracteres seguros; si no hay nada útil, {@code local}. */
    static String idReplica(Map<String, String> env) {
        String crudo = env.getOrDefault("RAILWAY_REPLICA_ID", "");
        if (crudo == null || crudo.isBlank()) {
            crudo = env.getOrDefault("HOSTNAME", "");
        }
        String limpio = crudo == null ? "" : crudo.replaceAll("[^a-zA-Z0-9-]", "");
        if (limpio.length() > 8) {
            limpio = limpio.substring(0, 8);
        }
        return limpio.isEmpty() ? "local" : limpio;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        response.setHeader("x-served-by", SERVIDO_POR);
        chain.doFilter(request, response);
    }
}
