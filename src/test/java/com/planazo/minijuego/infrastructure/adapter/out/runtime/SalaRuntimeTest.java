package com.planazo.minijuego.infrastructure.adapter.out.runtime;

import com.planazo.minijuego.application.dto.EstadoJuegoDTO;
import com.planazo.minijuego.application.service.SnapshotMapper;
import com.planazo.minijuego.domain.model.Direccion;
import com.planazo.minijuego.domain.model.EstadoSala;
import com.planazo.minijuego.domain.model.ResultadoPartida;
import com.planazo.minijuego.domain.model.ResumenSala;
import com.planazo.minijuego.domain.model.Sala;
import com.planazo.minijuego.domain.port.in.FinalizarPartidaUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El runtime de una sala sin hilos reales: el scheduler es un doble que
 * guarda el tick, y la prueba lo ejecuta a mano avanzando un reloj propio.
 */
class SalaRuntimeTest {

    /** Reloj que solo avanza cuando la prueba lo dice. */
    static final class Reloj extends Clock {
        private Instant ahora = Instant.parse("2026-01-01T00:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private final Reloj reloj = new Reloj();
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final ScheduledFuture<?> futuro = mock(ScheduledFuture.class);
    private final FinalizarPartidaUseCase finalizar = mock(FinalizarPartidaUseCase.class);
    private final GameBroadcaster broadcaster = mock(GameBroadcaster.class);
    private final AtomicInteger avisosLobby = new AtomicInteger();
    private Sala sala;
    private SalaRuntime runtime;

    @BeforeEach
    void preparar() {
        doReturnFuturo();
        sala = new Sala("s1", "SALA-01", 2, reloj);
        runtime = new SalaRuntime(sala, scheduler, finalizar, new SnapshotMapper(), broadcaster, avisosLobby::incrementAndGet);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void doReturnFuturo() {
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn((ScheduledFuture) futuro);
    }

    /** El tick que el runtime le entregó al scheduler. */
    private Runnable tick() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, atLeastOnce()).scheduleAtFixedRate(captor.capture(), eq(50L), eq(50L), eq(TimeUnit.MILLISECONDS));
        return captor.getValue();
    }

    private void arrancarPartida() {
        runtime.unirse("j1", "Ana");
        runtime.unirse("j2", "Beto");
        reloj.avanzar(Duration.ofSeconds(Sala.SEGUNDOS_CUENTA_REGRESIVA));
        tick().run();
        assertThat(sala.estado()).isEqualTo(EstadoSala.EN_CURSO);
    }

    @Test
    void unirse_esIdempotente_yAvisaALaSalaYAlLobby() {
        ResumenSala r = runtime.unirse("j1", "Ana");
        ResumenSala otraVez = runtime.unirse("j1", "Ana");

        assertThat(r.cantidadJugadores()).isEqualTo(1);
        assertThat(otraVez.cantidadJugadores()).isEqualTo(1);
        assertThat(runtime.tieneJugador("j1")).isTrue();
        assertThat(runtime.resumen().codigo()).isEqualTo("SALA-01");
        assertThat(runtime.jugadoresSiNoEstaEnPartida()).containsExactly("j1");
        verify(broadcaster, times(2)).difundirEstado(any(EstadoJuegoDTO.class));
        assertThat(avisosLobby.get()).isEqualTo(2);
        verify(scheduler, never()).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
    }

    @Test
    void conLaSalaLlena_arrancaElLoop_yLaCuentaRegresivaSeDifundeUnaVezPorSegundo() {
        runtime.unirse("j1", "Ana");
        runtime.unirse("j2", "Beto");
        assertThat(sala.estado()).isEqualTo(EstadoSala.CUENTA_REGRESIVA);
        Runnable tick = tick();
        assertThat(runtime.jugadoresSiNoEstaEnPartida()).containsExactly("j1", "j2");

        int antes = mockingDetailsCount();
        tick.run(); // primer segundo de la cuenta: se difunde
        tick.run(); // mismo segundo: no hay nada nuevo
        assertThat(mockingDetailsCount()).isEqualTo(antes + 1);

        reloj.avanzar(Duration.ofSeconds(Sala.SEGUNDOS_CUENTA_REGRESIVA));
        int lobbyAntes = avisosLobby.get();
        tick.run(); // arranca la partida: se difunde y se avisa al lobby
        assertThat(sala.estado()).isEqualTo(EstadoSala.EN_CURSO);
        assertThat(avisosLobby.get()).isEqualTo(lobbyAntes + 1);
        assertThat(runtime.jugadoresSiNoEstaEnPartida()).isEmpty();
    }

    private int mockingDetailsCount() {
        return (int) org.mockito.Mockito.mockingDetails(broadcaster).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("difundirEstado")).count();
    }

    @Test
    void enPartida_aplicaLosGiros_yDescartaLosInvalidos() {
        arrancarPartida();
        runtime.encolarMovimiento("j1", Direccion.ABAJO);
        runtime.encolarMovimiento("fantasma", Direccion.ARRIBA); // no está en la sala: se descarta
        reloj.avanzar(Duration.ofMillis(Sala.MILISEGUNDOS_ANTES_DEL_PRIMER_PASO));
        tick().run();
        assertThat(sala.pasosDados()).isEqualTo(1);
        assertThat(sala.estado()).isEqualTo(EstadoSala.EN_CURSO);
    }

    @Test
    void pausar_reanudar_desconectar_yReconectar_seDifunden() {
        arrancarPartida();
        runtime.pausar("j1");
        runtime.reanudar("j1");
        runtime.marcarDesconectado("j2");
        runtime.marcarReconectado("j2");
        runtime.marcarReconectado("nadie"); // también difunde para quien recién se suscribe
        verify(broadcaster, atLeastOnce()).difundirEstado(any());
        assertThat(sala.estado()).isEqualTo(EstadoSala.EN_CURSO);
    }

    @Test
    void conJugadoresQueNoEstan_lasOperacionesSonTolerantes() {
        runtime.salir("nadie");
        runtime.marcarDesconectado("nadie");
        runtime.pausar("nadie");
        runtime.reanudar("nadie");
        verify(broadcaster, never()).difundirEstado(any());
        assertThat(avisosLobby.get()).isZero();
    }

    @Test
    void salirAntesDePartida_cancelaLaCuenta_yDetieneElLoop() {
        runtime.unirse("j1", "Ana");
        runtime.unirse("j2", "Beto");
        runtime.salir("j2");
        assertThat(sala.estado()).isEqualTo(EstadoSala.ESPERANDO_JUGADORES);
        verify(futuro).cancel(false);
        verify(finalizar, never()).finalizarPartida(any());
    }

    @Test
    void desconectarseEnLaSalaDeEspera_loSacaDeLaSala() {
        runtime.unirse("j1", "Ana");
        runtime.marcarDesconectado("j1");
        assertThat(runtime.tieneJugador("j1")).isFalse();
    }

    @Test
    void siElRivalSeVaEnPartida_terminaRegistraElResultado_yVaciaLaSala() {
        arrancarPartida();
        runtime.salir("j2");

        ArgumentCaptor<ResultadoPartida> resultado = ArgumentCaptor.forClass(ResultadoPartida.class);
        verify(finalizar).finalizarPartida(resultado.capture());
        assertThat(resultado.getValue()).isNotNull();
        verify(broadcaster, atLeastOnce()).difundirEstado(any());
        assertThat(sala.estado()).isEqualTo(EstadoSala.ESPERANDO_JUGADORES);
        assertThat(sala.cantidadJugadores()).isZero();
    }

    @Test
    void alAgotarseElTiempo_elTickTerminaLaPartida_aunqueFallenPostgresElSocketYElLobby() {
        doThrow(new IllegalStateException("postgres caído")).when(finalizar).finalizarPartida(any());
        doThrow(new IllegalStateException("socket caído")).when(broadcaster).difundirEstado(any());
        runtime = new SalaRuntime(sala, scheduler, finalizar, new SnapshotMapper(), broadcaster, () -> {
            throw new IllegalStateException("lobby caído");
        });
        arrancarPartida();
        reloj.avanzar(Duration.ofSeconds(Sala.SEGUNDOS_LIMITE_PARTIDA));
        tick().run();

        verify(finalizar).finalizarPartida(any());
        assertThat(sala.estado()).isEqualTo(EstadoSala.ESPERANDO_JUGADORES);
    }

    @Test
    void unTickFueraDePartida_detieneElLoop() {
        runtime.unirse("j1", "Ana");
        runtime.unirse("j2", "Beto");
        Runnable tick = tick();
        sala.salir("j2"); // la sala vuelve a la espera sin pasar por el runtime
        tick.run();
        verify(futuro).cancel(false);
    }

    @Test
    void siElTickExplota_noTumbaElScheduler() {
        SnapshotMapper roto = mock(SnapshotMapper.class);
        when(roto.aEstadoJuegoDTO(any(), isNull())).thenThrow(new IllegalStateException("bug"));
        runtime = new SalaRuntime(sala, scheduler, finalizar, roto, broadcaster, avisosLobby::incrementAndGet);
        sala.unirse("j1", "Ana");
        sala.unirse("j2", "Beto");
        // Arranca el loop por la vía normal de unirse con un jugador ya presente (idempotente).
        try {
            runtime.unirse("j1", "Ana");
        } catch (IllegalStateException esperado) {
            // la difusión inicial falla con el mapper roto, pero el loop ya quedó programado
        }
        tick().run(); // la excepción se registra y no sale del tick
        assertThat(sala.estado()).isEqualTo(EstadoSala.CUENTA_REGRESIVA);
    }

    @Test
    void siYaHayUnLoopVivo_noProgramaOtro() {
        when(futuro.isDone()).thenReturn(false);
        runtime.unirse("j1", "Ana");
        runtime.unirse("j2", "Beto");
        runtime.unirse("j2", "Beto"); // vuelve a sincronizar con el loop vivo
        verify(scheduler, times(1)).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
    }
}
