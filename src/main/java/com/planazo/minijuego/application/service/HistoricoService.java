package com.planazo.minijuego.application.service;

import com.planazo.minijuego.domain.model.ResultadoPartida;
import com.planazo.minijuego.domain.port.in.ConsultarHistoricoUseCase;
import com.planazo.minijuego.domain.port.out.ResultadoPartidaRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;

/** Historia Should "ver el histórico de mis partidas". */
@Service
public class HistoricoService implements ConsultarHistoricoUseCase {

    private final ResultadoPartidaRepositoryPort resultadoRepository;

    public HistoricoService(ResultadoPartidaRepositoryPort resultadoRepository) {
        this.resultadoRepository = resultadoRepository;
    }

    @Override
    public List<ResultadoPartida> historicoDeJugador(String jugadorId) {
        return resultadoRepository.buscarPorJugador(jugadorId);
    }
}
