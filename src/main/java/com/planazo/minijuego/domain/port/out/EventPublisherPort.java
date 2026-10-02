package com.planazo.minijuego.domain.port.out;

import com.planazo.minijuego.domain.model.ResultadoPartida;

/**
 * Publica el hecho consumado "partida finalizada" hacia el resto de
 * Planazo (p.ej. un futuro servicio de logros o notificaciones). Fail-soft:
 * si RabbitMQ no responde, la partida ya quedó guardada en Postgres — no
 * tiene sentido hacer fallar la experiencia del jugador por un exchange
 * caído.
 */
public interface EventPublisherPort {

    void publicarPartidaFinalizada(ResultadoPartida resultado);
}
