package com.planazo.minijuego.infrastructure.adapter.out.messaging.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planazo.minijuego.domain.model.PuntajeJugador;
import com.planazo.minijuego.domain.model.ResultadoPartida;
import com.planazo.minijuego.domain.port.out.EventPublisherPort;
import com.planazo.minijuego.infrastructure.adapter.out.messaging.event.PartidaFinalizadaEventData;
import com.planazo.minijuego.infrastructure.adapter.out.messaging.event.RealtimeEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PartidaEventPublisherAdapter implements EventPublisherPort {

    private static final String CHANNEL = "planazo.events";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PartidaEventPublisherAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publicarPartidaFinalizada(ResultadoPartida resultado) {
        var puntajes = resultado.puntajes().stream()
                .map(this::aLineaPuntaje)
                .toList();

        var payload = new PartidaFinalizadaEventData(
                resultado.salaId(),
                resultado.salaCodigo(),
                resultado.ganadorJugadorId(),
                resultado.ganadorJugadorNombre(),
                resultado.motivoVictoria().name(),
                puntajes,
                resultado.iniciadaEn(),
                resultado.finalizadaEn()
        );

        RealtimeEvent event = RealtimeEvent.of(
                "GAME.PARTIDA_FINALIZADA",
                List.of("global"),
                payload
        );

        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(CHANNEL, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializando evento de partida finalizada para Redis", e);
        }
    }

    private PartidaFinalizadaEventData.LineaPuntaje aLineaPuntaje(PuntajeJugador puntaje) {
        return new PartidaFinalizadaEventData.LineaPuntaje(
                puntaje.jugadorId(), puntaje.jugadorNombre(), puntaje.puntaje(), puntaje.posicionFinal(), puntaje.sobrevivio()
        );
    }
}

