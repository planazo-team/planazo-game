package com.planazo.minijuego.infrastructure.adapter.out.persistence;

import com.planazo.minijuego.domain.model.ResultadoPartida;
import com.planazo.minijuego.domain.port.out.ResultadoPartidaRepositoryPort;
import com.planazo.minijuego.infrastructure.adapter.out.persistence.mapper.ResultadoPartidaMapper;
import com.planazo.minijuego.infrastructure.adapter.out.persistence.repository.ResultadoPartidaJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Fail-hard a propósito (ver {@code domain.port.out.ResultadoPartidaRepositoryPort}):
 * no hay try/catch aquí. Si Postgres no responde, la excepción se
 * propaga tal cual hacia {@code FinalizarPartidaService.finalizarPartida}, que la
 * deja subir sin capturarla — es la única escritura de este servicio que
 * no puede degradarse silenciosamente.
 */
@Component
public class JpaResultadoPartidaRepositoryAdapter implements ResultadoPartidaRepositoryPort {

    private final ResultadoPartidaJpaRepository repository;
    private final ResultadoPartidaMapper mapper;

    public JpaResultadoPartidaRepositoryAdapter(ResultadoPartidaJpaRepository repository, ResultadoPartidaMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void guardar(ResultadoPartida resultado) {
        repository.save(mapper.aEntidad(resultado));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResultadoPartida> buscarPorJugador(String jugadorId) {
        return repository.buscarPorJugador(jugadorId).stream().map(mapper::aDominio).toList();
    }
}
