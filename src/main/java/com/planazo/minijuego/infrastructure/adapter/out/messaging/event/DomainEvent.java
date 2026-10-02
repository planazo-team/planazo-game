package com.planazo.minijuego.infrastructure.adapter.out.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sobre estándar de evento de dominio (EDA), igual en forma al usado por
 * {@code mapa-descubrimiento-service} — cada microservicio de Planazo
 * mantiene su propia copia (principio de autonomía: nada de un módulo
 * padre compartido), pero el contrato del sobre es el mismo en todos por
 * convención de equipo.
 *
 * @param eventId       identificador único del evento, para deduplicar en el consumidor.
 * @param eventType     nombre lógico del evento, p.ej. {@code "PartidaFinalizada"}.
 * @param timestamp     instante en que se publicó.
 * @param source        qué servicio lo publicó — siempre {@code "minijuego-service"} aquí.
 * @param version       versión del esquema del payload, para poder evolucionarlo sin romper consumidores.
 * @param correlationId para trazar una cadena de eventos relacionados a través de varios servicios.
 * @param data          el payload específico del evento.
 */
public record DomainEvent<T>(
        String eventId,
        String eventType,
        Instant timestamp,
        String source,
        int version,
        String correlationId,
        T data
) {
    public static <T> DomainEvent<T> de(String eventType, T data) {
        return new DomainEvent<>(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                "minijuego-service",
                1,
                UUID.randomUUID().toString(),
                data
        );
    }
}
