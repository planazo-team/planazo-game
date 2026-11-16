package com.planazo.minijuego.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Seguridad stateless (principio SOA de ausencia de estado, igual que
 * {@code mapa-descubrimiento-service}): sin sesión de servidor, todo por
 * JWT. La diferencia frente al servicio hermano es el tipo de
 * {@link JwtDecoder} — ver {@code package-info.java} de este paquete
 * para el porqué del HS256 compartido en vez de OIDC por issuer-uri.
 *
 * <p><b>Autorización por rol:</b> un JWT válido solo prueba identidad
 * (401 si falta o expiró); el claim {@code roles} (lista de strings, p.ej.
 * {@code ["jugador"]}) decide autoridad. Este servicio hoy no tiene ningún
 * endpoint que distinga entre roles — todos los recursos de juego son del
 * jugador autenticado, sea cual sea su rol — así que nada usa
 * {@code @PreAuthorize} todavía. Lo que sí queda listo para cuando haga
 * falta: el claim se traduce a {@code GrantedAuthority} (prefijo
 * {@code ROLE_}, ver {@link #jwtAuthenticationConverter()}) y
 * {@link EnableMethodSecurity} está encendido, así que un futuro
 * {@code @PreAuthorize("hasRole('NEGOCIO')")} en un controlador funciona
 * sin tocar esta clase — y devuelve 403, no 401, si el rol no alcanza.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
                                "/actuator/health", "/actuator/health/**",
                                // Sin JWT porque las scrapea Prometheus, no un usuario del frontend;
                                // la exposición pública de métricas queda acotada por el firewall de
                                // Railway/el proxy, no por esta capa (ver escenario de Observabilidad).
                                "/actuator/metrics", "/actuator/metrics/**", "/actuator/prometheus",
                                "/ws", "/ws/**",
                                "/test-client.html", "/favicon.ico"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Lee el claim {@code roles} del JWT (lista de strings, p.ej.
     * {@code ["jugador"]}) y lo convierte en {@code GrantedAuthority} con
     * prefijo {@code ROLE_} (mayúsculas), que es lo que
     * {@code hasRole("...")} espera. Si el claim no viene (todos los JWT
     * reales hoy, y los de {@code dev-token} salvo que se pida otra cosa),
     * el usuario queda autenticado sin ninguna autoridad — sigue entrando
     * a todo lo que hoy es de cualquier jugador, nada cambia hasta que un
     * controlador use {@code @PreAuthorize}.
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::autoridadesDesdeClaimRoles);
        return converter;
    }

    private Collection<GrantedAuthority> autoridadesDesdeClaimRoles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .filter(rol -> rol != null && !rol.isBlank())
                .map(rol -> new SimpleGrantedAuthority("ROLE_" + rol.trim().toUpperCase(Locale.ROOT)))
                .map(GrantedAuthority.class::cast)
                .toList();
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
