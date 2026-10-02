package com.planazo.minijuego.domain.port.in;

import com.planazo.minijuego.domain.model.EntradaLeaderboard;

import java.util.List;

/** SNK-08 / RT-3 / CC-2: leer el marcador global, respaldado por Redis Sorted Set. */
public interface ConsultarLeaderboardUseCase {

    List<EntradaLeaderboard> topGlobal(int limite);
}
