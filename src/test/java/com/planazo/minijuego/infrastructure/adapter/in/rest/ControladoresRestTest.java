package com.planazo.minijuego.infrastructure.adapter.in.rest;

import com.planazo.minijuego.domain.exception.JugadorNoEnSalaException;
import com.planazo.minijuego.domain.exception.JugadorYaEnSalaException;
import com.planazo.minijuego.domain.exception.PartidaNoEnCursoException;
import com.planazo.minijuego.domain.exception.PartidaYaIniciadaException;
import com.planazo.minijuego.domain.exception.SalaLlenaException;
import com.planazo.minijuego.domain.exception.SalaNoEncontradaException;
import com.planazo.minijuego.domain.model.EntradaLeaderboard;
import com.planazo.minijuego.domain.model.ResumenSala;
import com.planazo.minijuego.domain.port.in.ConsultarHistoricoUseCase;
import com.planazo.minijuego.domain.port.in.ConsultarLeaderboardUseCase;
import com.planazo.minijuego.domain.port.in.GestionarSalasUseCase;
import com.planazo.minijuego.infrastructure.adapter.in.rest.dto.DevTokenRequest;
import com.planazo.minijuego.infrastructure.adapter.in.rest.dto.DevTokenResponse;
import com.planazo.minijuego.infrastructure.security.DevJwtTokenService;
import com.planazo.minijuego.infrastructure.security.JwtProperties;
import com.planazo.minijuego.infrastructure.security.UsuarioDePrueba;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ControladoresRestTest {

    private static Jwt jwt(String sub, String nombre) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "HS256").subject(sub).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (nombre != null) b.claim("nombre", nombre);
        return b.build();
    }

    private static final ResumenSala RESUMEN = new ResumenSala("s1", "SALA-01", 2, 1, "ESPERANDO_JUGADORES", List.of("Ana"), List.of("j1"));

    @Test
    void salas_listar_obtener_unirse_ySalir() {
        GestionarSalasUseCase uso = mock(GestionarSalasUseCase.class);
        when(uso.listarSalas()).thenReturn(List.of(RESUMEN));
        when(uso.obtenerSala("s1")).thenReturn(RESUMEN);
        when(uso.unirseASala("s1", "j1", "Ana")).thenReturn(RESUMEN);
        when(uso.unirseASala("s1", "j2", "j2")).thenReturn(RESUMEN);
        SalaController c = new SalaController(uso);

        assertThat(c.listar().data()).containsExactly(RESUMEN);
        assertThat(c.obtener("s1").data()).isEqualTo(RESUMEN);
        ResponseEntity<ApiResponse<ResumenSala>> creada = c.unirse("s1", jwt("j1", "Ana"));
        assertThat(creada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        c.unirse("s1", jwt("j2", null)); // sin nombre en el token usa el id
        verify(uso).unirseASala("s1", "j2", "j2");

        assertThat(c.salir("s1", "j1", jwt("j1", "Ana")).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Jwt otro = jwt("j9", "Intruso");
        assertThrows(AccessDeniedException.class, () -> c.salir("s1", "j1", otro));
    }

    @Test
    void leaderboardYHistorico_envuelvenLaRespuesta() {
        ConsultarLeaderboardUseCase lb = mock(ConsultarLeaderboardUseCase.class);
        when(lb.topGlobal(5)).thenReturn(List.of(new EntradaLeaderboard(1, "j1", "Ana", 9)));
        assertThat(new LeaderboardController(lb).top(5).data()).hasSize(1);

        ConsultarHistoricoUseCase h = mock(ConsultarHistoricoUseCase.class);
        when(h.historicoDeJugador("j1")).thenReturn(List.of());
        assertThat(new HistoricoController(h).misPartidas(jwt("j1", "Ana")).data()).isEmpty();
    }

    @Test
    void authDev_emiteTokensSoloParaUsuariosDePrueba() throws Exception {
        JwtProperties props = new JwtProperties();
        props.setSecreto("un-secreto-de-pruebas-de-al-menos-32-bytes!!");
        props.setExpiracionMinutosDevToken(5);
        AuthDevController c = new AuthDevController(new DevJwtTokenService(props));

        assertThat(c.usuariosDePrueba().data()).isEqualTo(UsuarioDePrueba.TODOS);
        ResponseEntity<?> ok = c.devToken(new DevTokenRequest("J-ANA"));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        DevTokenResponse cuerpo = ((ApiResponse<DevTokenResponse>) ok.getBody()).data();
        assertThat(SignedJWT.parse(cuerpo.token()).getJWTClaimsSet().getSubject()).isEqualTo("j-ana");
        assertThat(props.getExpiracionMinutosDevToken()).isEqualTo(5);

        assertThat(c.devToken(new DevTokenRequest("nadie")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void elServicioDeTokens_conUnSecretoCorto_fallaConMensajeClaro() {
        JwtProperties props = new JwtProperties();
        props.setSecreto("corto");
        DevJwtTokenService servicio = new DevJwtTokenService(props);
        assertThrows(IllegalStateException.class, () -> servicio.generarToken("j-ana", "Ana"));
    }

    @Test
    void elManejadorGlobal_traduceCadaErrorASuStatus() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();
        assertThat(h.manejarSalaNoEncontrada(new SalaNoEncontradaException("s1")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(h.manejarSalaLlena(new SalaLlenaException("SALA-01", 2)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(h.manejarPartidaYaIniciada(new PartidaYaIniciadaException("SALA-01")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(h.manejarJugadorYaEnSala(new JugadorYaEnSalaException("j1")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(h.manejarJugadorNoEnSala(new JugadorNoEnSalaException("j1")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(h.manejarPartidaNoEnCurso(new PartidaNoEnCursoException("s1")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(h.manejarDomainException(new SalaNoEncontradaException("s2")).getBody().codigo()).isNotBlank();
        assertThat(h.manejarAccesoDenegado(new AccessDeniedException("no")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(h.manejarIllegalArgument(new IllegalArgumentException("x")).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(h.manejarRutaInexistente(new NoResourceFoundException(HttpMethod.GET, "/nada")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(h.manejarMetodoNoSoportado(new HttpRequestMethodNotSupportedException("PATCH")).getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        ResponseEntity<ApiError> inesperado = h.manejarError(new RuntimeException("boom"));
        assertThat(inesperado.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(inesperado.getBody().codigo()).isEqualTo("ERROR_INESPERADO");
    }
}
