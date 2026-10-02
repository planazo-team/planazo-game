package com.planazo.minijuego.infrastructure.adapter.out.messaging.publisher;

import com.planazo.minijuego.domain.model.PuntajeJugador;
import com.planazo.minijuego.domain.model.ResultadoPartida;
import com.planazo.minijuego.domain.port.out.EventPublisherPort;
import com.planazo.minijuego.infrastructure.adapter.out.messaging.event.DomainEvent;
import com.planazo.minijuego.infrastructure.adapter.out.messaging.event.PartidaFinalizadaEventData;
import com.planazo.minijuego.infrastructure.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PartidaEventPublisherAdapter implements EventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    public PartidaEventPublisherAdapter(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
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

        DomainEvent<PartidaFinalizadaEventData> evento = DomainEvent.de("PartidaFinalizada", payload);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_EVENTOS,
                "minijuego.partida.finalizada",
                evento
        );
    }

    private PartidaFinalizadaEventData.LineaPuntaje aLineaPuntaje(PuntajeJugador puntaje) {
        return new PartidaFinalizadaEventData.LineaPuntaje(
                puntaje.jugadorId(), puntaje.jugadorNombre(), puntaje.puntaje(), puntaje.posicionFinal(), puntaje.sobrevivio()
        );
    }
}
