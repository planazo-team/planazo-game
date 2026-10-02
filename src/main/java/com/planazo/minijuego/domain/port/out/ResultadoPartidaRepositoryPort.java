package com.planazo.minijuego.domain.port.out;

import com.planazo.minijuego.domain.model.ResultadoPartida;

import java.util.List;

/**
 * Persistencia del resultado de cada partida (SNK-08, CC-2: "que mi
 * puntaje quede registrado sin perderse"). Fail-hard a propósito: si
 * Postgres no responde, se propaga la excepción — es la única fuente de
 * verdad, a diferencia del leaderboard en Redis, que es una vista
 * derivada y desechable.
 */
public interface ResultadoPartidaRepositoryPort {

    void guardar(ResultadoPartida resultado);

    List<ResultadoPartida> buscarPorJugador(String jugadorId);
}
