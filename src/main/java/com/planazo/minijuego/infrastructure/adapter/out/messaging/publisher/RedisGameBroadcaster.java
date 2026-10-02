package com.planazo.minijuego.infrastructure.adapter.out.messaging.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planazo.minijuego.application.dto.EstadoJuegoDTO;
import com.planazo.minijuego.domain.model.ResumenSala;
import com.planazo.minijuego.infrastructure.adapter.out.messaging.event.RealtimeEvent;
import com.planazo.minijuego.infrastructure.adapter.out.runtime.GameBroadcaster;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisGameBroadcaster implements GameBroadcaster {

    private static final String CHANNEL = "planazo.events";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisGameBroadcaster(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void difundirEstado(EstadoJuegoDTO estado) {
        RealtimeEvent event = RealtimeEvent.of(
                "GAME.STATE_UPDATE",
                List.of("salas:" + estado.salaId()),
                estado
        );
        publish(event);
    }

    @Override
    public void difundirSalas(List<ResumenSala> salas) {
        RealtimeEvent event = RealtimeEvent.of(
                "LOBBY.ROOMS_UPDATE",
                List.of("salas"),
                salas
        );
        publish(event);
    }

    private void publish(RealtimeEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(CHANNEL, json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializando evento realtime para Redis", e);
        }
    }
}
