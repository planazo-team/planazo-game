package com.planazo.minijuego.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Seguridad stateless (principio SOA de ausencia de estado, igual que
 * {@code mapa-descubrimiento-service}): sin sesión de servidor, todo por
 * JWT. La diferencia frente al servicio hermano es el tipo de
 * {@link JwtDecoder} — ver {@code package-info.java} de este paquete
 * para el porqué del HS256 compartido en vez de OIDC por issuer-uri.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtProperties jwtProperties;
    private final List<String> origenesPermitidos;

    public SecurityConfig(JwtProperties jwtProperties,
                          @Value("${planazo.cors.origenes}") List<String> origenesPermitidos) {
        this.jwtProperties = jwtProperties;
        this.origenesPermitidos = origenesPermitidos;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec clave = new SecretKeySpec(
                jwtProperties.getSecreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(clave).build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // CSRF no aplica: API sin estado con JWT en el header Authorization, sin cookies
                // de sesión que el navegador mande solo (SessionCreationPolicy.STATELESS).
                .csrf(csrf -> csrf.disable()) // NOSONAR: java:S4502 revisado, ver comentario
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
                                "/actuator/health", "/actuator/health/**",
                                "/ws", "/ws/**",
                                "/test-client.html", "/favicon.ico"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder())));
        return http.build();
    }

    /**
     * Solo los orígenes del frontend (CORS_ORIGINS, separados por coma).
     * Acepta patrones como {@code https://planazo-frontend-*.vercel.app} para
     * los previews de Vercel. El WebSocket STOMP pasa por la misma regla.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origenesPermitidos.stream().map(String::trim).filter(o -> !o.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
