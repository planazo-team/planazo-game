/**
 * Publicadores de eventos hacia RabbitMQ. Fail-soft: si el broker no
 * responde, {@code PartidaEventPublisherAdapter} deja que la excepción
 * suba y es {@code FinalizarPartidaService.finalizarPartida} quien la atrapa y
 * solo la registra — el evento es una notificación de mejor esfuerzo
 * hacia otros servicios, nunca la fuente de verdad del resultado.
 */
package com.planazo.minijuego.infrastructure.adapter.out.messaging.publisher;
