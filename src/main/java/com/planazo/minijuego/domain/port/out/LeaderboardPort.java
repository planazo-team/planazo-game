package com.planazo.minijuego.domain.port.out;

import com.planazo.minijuego.domain.model.EntradaLeaderboard;

import java.util.List;

/**
 * SNK-08 / RT-3 / CC-2: el marcador en vivo. Respaldado por un Redis
 * Sorted Set (ZINCRBY/ZREVRANGE) — la estructura de datos hecha
 * exactamente para esto: incrementos atómicos y lectura ordenada sin
 * necesitar ningún lock de aplicación. Fail-soft: si Redis no responde,
 * el resultado de la partida de todas formas ya quedó a salvo en
 * Postgres vía {@link ResultadoPartidaRepositoryPort}; el leaderboard en
 * vivo simplemente no se actualiza ese instante.
 */
public interface LeaderboardPort {

    void registrarPuntaje(String jugadorId, String jugadorNombre, int puntajeGanado);

    List<EntradaLeaderboard> topN(int n);
}
