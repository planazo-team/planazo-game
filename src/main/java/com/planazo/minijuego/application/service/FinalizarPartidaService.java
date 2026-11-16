package com.planazo.minijuego.application.service;

import com.planazo.minijuego.domain.model.PuntajeJugador;
import com.planazo.minijuego.domain.model.ResultadoPartida;
import com.planazo.minijuego.domain.port.in.FinalizarPartidaUseCase;
import com.planazo.minijuego.domain.port.out.EventPublisherPort;
import com.planazo.minijuego.domain.port.out.LeaderboardPort;
import com.planazo.minijuego.domain.port.out.ResultadoPartidaRepositoryPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SNK-09/SNK-10. Separado de {@link PartidaService} porque
 * {@code SalaRuntimeRegistry} depende de este caso de uso y
 * {@link PartidaService} depende de {@code SalaRuntimeRegistry}: tenerlos
 * en el mismo bean formaba un ciclo de dependencias.
 */
@Service
public class FinalizarPartidaService implements FinalizarPartidaUseCase {

    private static final Logger log = LoggerFactory.getLogger(FinalizarPartidaService.class);

    private final ResultadoPartidaRepositoryPort resultadoRepository;
    private final LeaderboardPort leaderboard;
    private final EventPublisherPort eventPublisher;
    private final MeterRegistry meterRegistry;
    private final Counter puntajesRegistrados;

    public FinalizarPartidaService(ResultadoPartidaRepositoryPort resultadoRepository,
                                    LeaderboardPort leaderboard,
                                    EventPublisherPort eventPublisher,
                                    MeterRegistry meterRegistry) {
        this.resultadoRepository = resultadoRepository;
        this.leaderboard = leaderboard;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        this.puntajesRegistrados = meterRegistry.counter("minijuego.puntajes.registrados");
    }

    /**
     * Orquesta lo que tiene que pasar al terminar una partida: Postgres
     * primero y fail-hard (es la fuente de verdad, CC-2), Redis y RabbitMQ
     * después y fail-soft (mejoras de experiencia — un fallo ahí se
     * registra y se sigue, nunca rompe el cierre de la partida).
     */
    @Override
    public void finalizarPartida(ResultadoPartida resultado) {
        resultadoRepository.guardar(resultado);
        meterRegistry.counter("minijuego.partidas.jugadas", "motivo", resultado.motivoVictoria().name()).increment();

        try {
            for (PuntajeJugador puntaje : resultado.puntajes()) {
                leaderboard.registrarPuntaje(puntaje.jugadorId(), puntaje.jugadorNombre(), puntaje.puntaje());
                puntajesRegistrados.increment();
            }
        } catch (Exception ex) {
            log.warn("No se pudo actualizar el leaderboard en Redis para la partida de la sala {}: {}",
                    resultado.salaId(), ex.getMessage());
        }

        try {
            eventPublisher.publicarPartidaFinalizada(resultado);
        } catch (Exception ex) {
            log.warn("No se pudo publicar PartidaFinalizadaEvent para la sala {}: {}",
                    resultado.salaId(), ex.getMessage());
        }
    }
}
