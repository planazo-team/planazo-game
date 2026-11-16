package com.planazo.minijuego.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.planazo.minijuego.application.dto.EstadoJuegoDTO;
import com.planazo.minijuego.domain.model.MotivoVictoria;
import com.planazo.minijuego.domain.model.PuntajeJugador;
import com.planazo.minijuego.domain.model.ResultadoPartida;
import com.planazo.minijuego.domain.model.ResumenSala;
import com.planazo.minijuego.infrastructure.adapter.out.messaging.event.DomainEvent;
import com.planazo.minijuego.infrastructure.adapter.out.messaging.publisher.PartidaEventPublisherAdapter;
import com.planazo.minijuego.infrastructure.adapter.out.messaging.publisher.RedisGameBroadcaster;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicadoresRedisTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    private JsonNode publicado() throws Exception {
        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq("planazo.events"), mensaje.capture());
        return json.readTree(mensaje.getValue());
    }

    @Test
    void laPartidaFinalizada_viajaPorElBusConSuPodio() throws Exception {
        ResultadoPartida r = new ResultadoPartida("s1", "SALA-01", "j1", "Ana", MotivoVictoria.MAYOR_PUNTAJE_POR_TIEMPO,
                List.of(new PuntajeJugador("j1", "Ana", 9, 1, true)),
                Instant.parse("2026-11-16T10:00:00Z"), Instant.parse("2026-11-16T10:03:00Z"));
        new PartidaEventPublisherAdapter(redis, json).publicarPartidaFinalizada(r);
        JsonNode evento = publicado();
        assertThat(evento.get("type").asText()).isEqualTo("GAME.PARTIDA_FINALIZADA");
        assertThat(evento.get("topics").get(0).asText()).isEqualTo("global");
        assertThat(evento.get("payload").toString()).contains("SALA-01", "MAYOR_PUNTAJE_POR_TIEMPO", "Ana");
    }

    @Test
    void elEstadoDeUnaSala_seDifundeEnElTemaDeEsaSala() throws Exception {
        EstadoJuegoDTO estado = new EstadoJuegoDTO("s9", "SALA-09", "EN_CURSO", 2, 0, 24, 18, List.of(), null, 120, null);
        new RedisGameBroadcaster(redis, json).difundirEstado(estado);
        JsonNode evento = publicado();
        assertThat(evento.get("type").asText()).isEqualTo("GAME.STATE_UPDATE");
        assertThat(evento.get("topics").get(0).asText()).isEqualTo("salas:s9");
        assertThat(evento.get("payload").get("salaCodigo").asText()).isEqualTo("SALA-09");
    }

    @Test
    void laListaDeSalas_seDifundeAlLobby() throws Exception {
        new RedisGameBroadcaster(redis, json).difundirSalas(
                List.of(new ResumenSala("s1", "SALA-01", 2, 0, "ESPERANDO_JUGADORES", List.of(), List.of())));
        JsonNode evento = publicado();
        assertThat(evento.get("type").asText()).isEqualTo("LOBBY.ROOMS_UPDATE");
        assertThat(evento.get("topics").get(0).asText()).isEqualTo("salas");
    }

    @Test
    void siNoSePuedeSerializar_fallaConUnMensajeClaro() throws Exception {
        ObjectMapper roto = mock(ObjectMapper.class);
        when(roto.writeValueAsString(any())).thenThrow(new JsonProcessingException("malo") { });
        RedisGameBroadcaster broadcaster = new RedisGameBroadcaster(redis, roto);
        List<ResumenSala> vacia = List.of();
        assertThrows(RuntimeException.class, () -> broadcaster.difundirSalas(vacia));
        ResultadoPartida r = new ResultadoPartida("s1", "S", null, null, MotivoVictoria.EMPATE_TODOS_ELIMINADOS, List.of(), null, null);
        PartidaEventPublisherAdapter publicador = new PartidaEventPublisherAdapter(redis, roto);
        assertThrows(RuntimeException.class, () -> publicador.publicarPartidaFinalizada(r));
    }

    @Test
    void elEventoDeDominio_traeIdsYFuente() {
        DomainEvent<String> e = DomainEvent.de("X.Y", "dato");
        assertThat(e.eventId()).isNotBlank();
        assertThat(e.correlationId()).isNotEqualTo(e.eventId());
        assertThat(e.source()).isEqualTo("minijuego-service");
        assertThat(e.version()).isEqualTo(1);
        assertThat(e.eventType()).isEqualTo("X.Y");
        assertThat(e.timestamp()).isNotNull();
        assertThat(e.data()).isEqualTo("dato");
    }
}
