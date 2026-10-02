package com.planazo.minijuego.infrastructure.security;

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

    public SecurityConfig(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
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
                .csrf(csrf -> csrf.disable())
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
     * Permisivo a propósito: este servicio no sirve todavía un Panel en
     * producción desde otro origen, pero durante el desarrollo es común
     * probarlo desde un frontend en {@code localhost} con otro puerto.
     * Si este servicio llega a producción expuesto a internet, esta
     * configuración debe restringirse a los orígenes reales del Panel.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
