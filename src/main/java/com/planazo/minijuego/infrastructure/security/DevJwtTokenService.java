package com.planazo.minijuego.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Emite JWTs HS256 firmados con el mismo secreto que
 * {@code SecurityConfig} usa para validarlos — un atajo de SOLO
 * DESARROLLO para poder probar este microservicio por Swagger o con el
 * cliente de juego sin necesitar el microservicio real de Registro y
 * autenticación corriendo. Nunca se expone en producción (ver
 * {@code SecurityConfig}, que solo registra el controlador que usa esta
 * clase bajo el perfil {@code dev}/{@code default}, nunca {@code prod}).
 */
@Service
public class DevJwtTokenService {

    private final JwtProperties jwtProperties;

    public DevJwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generarToken(String jugadorId, String jugadorNombre) {
        return generarToken(jugadorId, jugadorNombre, null);
    }

    /**
     * {@code roles} alimenta el claim {@code roles} del JWT (infraestructura
     * de autorización, ver {@code SecurityConfig#jwtAuthenticationConverter}):
     * si se omite, el token sale con el rol por defecto {@code jugador}.
     */
    public String generarToken(String jugadorId, String jugadorNombre, List<String> roles) {
        try {
            Instant ahora = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(jugadorId)
                    .claim("nombre", jugadorNombre)
                    .claim("roles", roles == null || roles.isEmpty() ? List.of("jugador") : roles)
                    .issuer("minijuego-service-dev")
                    .issueTime(Date.from(ahora))
                    .expirationTime(Date.from(ahora.plus(Duration.ofMinutes(jwtProperties.getExpiracionMinutosDevToken()))))
                    .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJWT.sign(new MACSigner(secretoComoBytes()));
            return signedJWT.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo firmar el JWT de desarrollo: " + ex.getMessage(), ex);
        }
    }

    private byte[] secretoComoBytes() {
        return jwtProperties.getSecreto().getBytes(StandardCharsets.UTF_8);
    }
}
