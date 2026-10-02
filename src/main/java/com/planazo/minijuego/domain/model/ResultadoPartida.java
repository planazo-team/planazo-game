package com.planazo.minijuego.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * El "hecho consumado" de una partida terminada — lo que satisface la
 * historia SNK-10 ("Ver quién ganó al terminar la partida") y, guardado en
 * Postgres, también SNK-08 ("Que mi puntaje quede registrado sin
 * perderse", CC-2). Es el objeto que cruza las tres fronteras del sistema:
 * se persiste (fuente de verdad), se publica como evento de dominio
 * (RabbitMQ, para quien quiera reaccionar — p.ej. un futuro servicio de
 * logros) y se refleja en el leaderboard (Redis, para lectura en vivo).
 */
public record ResultadoPartida(
        String salaId,
        String salaCodigo,
        String ganadorJugadorId,
        String ganadorJugadorNombre,
        MotivoVictoria motivoVictoria,
        List<PuntajeJugador> puntajes,
        Instant iniciadaEn,
        Instant finalizadaEn
) {
}
