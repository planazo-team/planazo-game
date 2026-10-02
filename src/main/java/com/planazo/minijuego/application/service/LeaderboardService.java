package com.planazo.minijuego.application.service;

import com.planazo.minijuego.domain.model.EntradaLeaderboard;
import com.planazo.minijuego.domain.port.in.ConsultarLeaderboardUseCase;
import com.planazo.minijuego.domain.port.out.LeaderboardPort;
import org.springframework.stereotype.Service;

import java.util.List;

/** SNK-08 / CC-2. */
@Service
public class LeaderboardService implements ConsultarLeaderboardUseCase {

    private final LeaderboardPort leaderboard;

    public LeaderboardService(LeaderboardPort leaderboard) {
        this.leaderboard = leaderboard;
    }

    @Override
    public List<EntradaLeaderboard> topGlobal(int limite) {
        return leaderboard.topN(limite);
    }
}
