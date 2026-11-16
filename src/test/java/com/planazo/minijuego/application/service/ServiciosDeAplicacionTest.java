package com.planazo.minijuego.application.service;

import com.planazo.minijuego.domain.exception.SalaNoEncontradaException;
import com.planazo.minijuego.domain.model.Direccion;
import com.planazo.minijuego.domain.model.EntradaLeaderboard;
import com.planazo.minijuego.domain.model.MotivoVictoria;
import com.planazo.minijuego.domain.model.PuntajeJugador;
import com.planazo.minijuego.domain.model.ResultadoPartida;
import com.planazo.minijuego.domain.model.ResumenSala;
import com.planazo.minijuego.domain.port.out.EventPublisherPort;
import com.planazo.minijuego.domain.port.out.GestorDeSalasEnVivoPort;
import com.planazo.minijuego.domain.port.out.LeaderboardPort;
import com.planazo.minijuego.domain.port.out.ResultadoPartidaRepositoryPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiciosDeAplicacionTest {

    private final GestorDeSalasEnVivoPort salas = mock(GestorDeSalasEnVivoPort.class);
    private final ResultadoPartidaRepositoryPort repo = mock(ResultadoPartidaRepositoryPort.class);
    private final LeaderboardPort leaderboard = mock(LeaderboardPort.class);
    private final EventPublisherPort eventos = mock(EventPublisherPort.class);
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private static final ResultadoPartida RESULTADO = new ResultadoPartida("s1", "SALA-01", "j1", "Ana",
            MotivoVictoria.ULTIMO_EN_PIE,
            List.of(new PuntajeJugador("j1", "Ana", 5, 1, true), new PuntajeJugador("j2", "Beto", 2, 2, false)), null, null);

    @Test
    void finalizar_guarda_sumaAlLeaderboard_yPublica() {
        new FinalizarPartidaService(repo, leaderboard, eventos, meterRegistry).finalizarPartida(RESULTADO);
        verify(repo).guardar(RESULTADO);
        verify(leaderboard).registrarPuntaje("j1", "Ana", 5);
        verify(leaderboard).registrarPuntaje("j2", "Beto", 2);
        verify(eventos).publicarPartidaFinalizada(RESULTADO);
    }

    @Test
    void finalizar_siRedisOElBusFallan_elResultadoQuedaGuardadoIgual() {
        doThrow(new IllegalStateException("redis")).when(leaderboard).registrarPuntaje(anyString(), anyString(), anyInt());
        doThrow(new IllegalStateException("bus")).when(eventos).publicarPartidaFinalizada(any());
        new FinalizarPartidaService(repo, leaderboard, eventos, meterRegistry).finalizarPartida(RESULTADO);
        verify(repo).guardar(RESULTADO);
    }

    @Test
    void partida_delegaCadaAccionEnLasSalasEnVivo() {
        PartidaService partida = new PartidaService(salas);
        partida.moverSerpiente("s1", "j1", Direccion.ARRIBA);
        partida.manejarDesconexion("s1", "j1");
        partida.manejarReconexion("s1", "j1");
        partida.pausarSerpiente("s1", "j1");
        partida.reanudarSerpiente("s1", "j1");
        partida.retirarJugadorInactivo("s1", "j1");
        when(salas.jugadoresEnEspera()).thenReturn(Map.of("s1", List.of("j1")));
        assertThat(partida.jugadoresEnSalaDeEspera()).containsEntry("s1", List.of("j1"));

        verify(salas).encolarMovimiento("s1", "j1", Direccion.ARRIBA);
        verify(salas).marcarDesconectado("s1", "j1");
        verify(salas).marcarReconectado("s1", "j1");
        verify(salas).pausar("s1", "j1");
        verify(salas).reanudar("s1", "j1");
        verify(salas).salir("s1", "j1");
    }

    @Test
    void salas_listaObtieneUneYSaca_yUnaSalaInexistenteDa404() {
        SalaService servicio = new SalaService(salas);
        ResumenSala resumen = new ResumenSala("s1", "SALA-01", 2, 1, "ESPERANDO_JUGADORES", List.of("Ana"), List.of("j1"));
        when(salas.listarSalas()).thenReturn(List.of(resumen));
        when(salas.obtenerSala("s1")).thenReturn(resumen);
        when(salas.unirse("s1", "j1", "Ana")).thenReturn(resumen);

        assertThat(servicio.listarSalas()).containsExactly(resumen);
        assertThat(servicio.obtenerSala("s1")).isEqualTo(resumen);
        assertThat(servicio.unirseASala("s1", "j1", "Ana")).isEqualTo(resumen);
        servicio.salirDeSala("s1", "j1");
        verify(salas).salir("s1", "j1");
        assertThrows(SalaNoEncontradaException.class, () -> servicio.obtenerSala("nada"));
    }

    @Test
    void leaderboardEHistorico_leenDeSusPuertos() {
        when(leaderboard.topN(3)).thenReturn(List.of(new EntradaLeaderboard(1, "j1", "Ana", 10)));
        when(repo.buscarPorJugador("j1")).thenReturn(List.of(RESULTADO));
        assertThat(new LeaderboardService(leaderboard).topGlobal(3)).hasSize(1);
        assertThat(new HistoricoService(repo).historicoDeJugador("j1")).containsExactly(RESULTADO);
    }
}
