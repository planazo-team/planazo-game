package com.planazo.minijuego.infrastructure.adapter.in.websocket;

import com.planazo.minijuego.application.dto.EstadoJuegoDTO;
import com.planazo.minijuego.domain.model.ResumenSala;
import com.planazo.minijuego.infrastructure.adapter.out.runtime.GameBroadcaster;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implementación real de {@code GameBroadcaster} — la interfaz la define
 * {@code infrastructure.adapter.out.runtime} (quien la consume), esta
 * clase solo la satisface con {@link SimpMessagingTemplate}. RT-3: cada
 * tick del game loop termina difundiendo aquí.
 */
@Component
public class StompGameBroadcaster implements GameBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public StompGameBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void difundirEstado(EstadoJuegoDTO estado) {
        messagingTemplate.convertAndSend("/topic/salas/" + estado.salaId(), estado);
    }

    @Override
    public void difundirSalas(List<ResumenSala> salas) {
        messagingTemplate.convertAndSend("/topic/salas", salas);
    }
}
