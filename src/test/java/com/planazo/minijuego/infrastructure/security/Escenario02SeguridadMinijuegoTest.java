package com.planazo.minijuego.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.planazo.minijuego.domain.port.in.ConsultarHistoricoUseCase;
import com.planazo.minijuego.domain.port.in.GestionarSalasUseCase;
import com.planazo.minijuego.infrastructure.adapter.in.rest.HistoricoController;
import com.planazo.minijuego.infrastructure.adapter.in.rest.SalaController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Pruebas automatizadas del Escenario ESC-02 (Seguridad: Autorización de peticiones al Minijuego).
 * Valida HU-110, HU-111, HU-112 y HU-113:
 * 1. Peticiones REST sin JWT o con JWT inválido/expirado son rechazadas.
 * 2. Un jugador solo actúa y consulta a su propio nombre (403 Forbidden ante suplantación).
 * 3. STOMP CONNECT rechaza conexiones sin JWT válido en menos de 200 ms.
 * 4. Cero invocaciones a la capa de dominio cuando la autorización falla.
 */
@ExtendWith(MockitoExtension.class)
class Escenario02SeguridadMinijuegoTest {

    private static final String SECRETO_TEST = "secreto-de-prueba-planazo-minijuego-seguridad-2026-min-32-chars";

    @Mock
    private GestionarSalasUseCase gestionarSalasUseCase;

    @Mock
    private ConsultarHistoricoUseCase consultarHistoricoUseCase;

    @Mock
    private MessageChannel messageChannel;

    private JwtDecoder jwtDecoder;
    private StompAuthChannelInterceptor stompInterceptor;
    private SalaController salaController;
    private HistoricoController historicoController;

    @BeforeEach
    void setUp() {
        SecretKeySpec keySpec = new SecretKeySpec(SECRETO_TEST.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        jwtDecoder = NimbusJwtDecoder.withSecretKey(keySpec).build();
        stompInterceptor = new StompAuthChannelInterceptor(jwtDecoder);
        salaController = new SalaController(gestionarSalasUseCase);
        historicoController = new HistoricoController(consultarHistoricoUseCase);
    }

    private String generarToken(String jugadorId, String nombre, Duration duracion) throws Exception {
        Instant ahora = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(jugadorId)
                .claim("nombre", nombre)
                // Un token vencido se emitió antes de vencer: iat < exp (si no, Nimbus lo rechaza por otra razón).
                .issueTime(Date.from(duracion.isNegative() ? ahora.plus(duracion).minus(Duration.ofMinutes(15)) : ahora))
                .expirationTime(Date.from(ahora.plus(duracion)))
                .build();
        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJWT.sign(new MACSigner(SECRETO_TEST.getBytes(StandardCharsets.UTF_8)));
        return signedJWT.serialize();
    }

    @Test
    @DisplayName("HU-110 / ESC-02: El decoder rechaza token expirado lanzando excepción de validación")
    void tokenExpiradoEsRechazado() throws Exception {
        String tokenExpirado = generarToken("j1", "Jugador 1", Duration.ofMinutes(-10));

        assertThatThrownBy(() -> jwtDecoder.decode(tokenExpirado))
                .isInstanceOf(JwtValidationException.class);

        verifyNoInteractions(gestionarSalasUseCase);
        verifyNoInteractions(consultarHistoricoUseCase);
    }

    @Test
    @DisplayName("HU-110 / ESC-02: El decoder rechaza token manipulado con firma inválida")
    void tokenManipuladoEsRechazado() throws Exception {
        String tokenValido = generarToken("j1", "Jugador 1", Duration.ofMinutes(15));
        String tokenManipulado = tokenValido.substring(0, tokenValido.length() - 5) + "abcde";

        assertThatThrownBy(() -> jwtDecoder.decode(tokenManipulado))
                .isInstanceOf(Exception.class);

        verifyNoInteractions(gestionarSalasUseCase);
    }

    @Test
    @DisplayName("HU-112 / ESC-02: Un jugador no puede hacer salir a otro jugador de la sala (403 AccessDenied)")
    void suplantacionDeJugadorEnSalirLanzaAccessDeniedYNoTocaDominio() {
        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "HS256")
                .claim("sub", "jugador-autenticado")
                .claim("nombre", "Jugador Autenticado")
                .build();

        assertThatThrownBy(() -> salaController.salir("sala-1", "otro-jugador", jwt))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("No puedes hacer salir a otro jugador");

        // 0 llamadas al caso de uso de dominio
        verify(gestionarSalasUseCase, never()).salirDeSala(anyString(), anyString());
    }

    @Test
    @DisplayName("HU-112 / ESC-02: Un jugador solo puede consultar su propio historial")
    void consultarHistoricoUsaExclusivamenteElSubjectDelJwt() {
        Jwt jwt = Jwt.withTokenValue("mock-jwt")
                .header("alg", "HS256")
                .claim("sub", "mi-jugador-id")
                .claim("nombre", "Mi Nombre")
                .build();

        when(consultarHistoricoUseCase.historicoDeJugador("mi-jugador-id")).thenReturn(List.of());

        historicoController.misPartidas(jwt);

        verify(consultarHistoricoUseCase, times(1)).historicoDeJugador("mi-jugador-id");
        verifyNoMoreInteractions(consultarHistoricoUseCase);
    }

    @Test
    @DisplayName("HU-111 / ESC-02: STOMP rechaza el CONNECT si falta el token en menos de 200 ms")
    void stompConnectSinTokenRechazaEnMenosDe200ms() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        long inicio = System.currentTimeMillis();

        assertThatThrownBy(() -> stompInterceptor.preSend(message, messageChannel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Falta el JWT");

        long duracion = System.currentTimeMillis() - inicio;
        assertThat(duracion).isLessThan(200);

        verifyNoInteractions(gestionarSalasUseCase);
    }

    @Test
    @DisplayName("HU-111 / ESC-02: STOMP acepta CONNECT con token válido y resuelve el Principal")
    void stompConnectConTokenValidoResuelvePrincipal() throws Exception {
        String tokenValido = generarToken("jugador-stomp-1", "Jugador Stomp", Duration.ofMinutes(15));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + tokenValido);
        // Como en el canal real de Spring: el interceptor debe poder fijar el usuario en los headers.
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> resultado = stompInterceptor.preSend(message, messageChannel);
        assertThat(resultado).isNotNull();

        StompHeaderAccessor accessorActualizado = StompHeaderAccessor.wrap(resultado);
        assertThat(accessorActualizado.getUser()).isNotNull();
        assertThat(accessorActualizado.getUser().getName()).isEqualTo("jugador-stomp-1");
    }
}
