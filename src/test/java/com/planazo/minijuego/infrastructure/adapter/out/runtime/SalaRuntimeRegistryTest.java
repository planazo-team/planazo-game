package com.planazo.minijuego.infrastructure.adapter.out.runtime;

import com.planazo.minijuego.application.service.SnapshotMapper;
import com.planazo.minijuego.domain.exception.SalaNoEncontradaException;
import com.planazo.minijuego.domain.model.DefinicionSala;
import com.planazo.minijuego.domain.model.Direccion;
import com.planazo.minijuego.domain.model.ResumenSala;
import com.planazo.minijuego.domain.port.in.FinalizarPartidaUseCase;
import com.planazo.minijuego.domain.port.out.SalaCatalogoPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalaRuntimeRegistryTest {

    private final SalaCatalogoPort catalogo = mock(SalaCatalogoPort.class);
    private final GameBroadcaster broadcaster = mock(GameBroadcaster.class);
    private SalaRuntimeRegistry registro;

    @BeforeEach
    void cargar() {
        when(catalogo.listarTodas()).thenReturn(List.of(
                new DefinicionSala("s2", "SALA-02", 3),
                new DefinicionSala("s1", "SALA-01", 3)));
        registro = new SalaRuntimeRegistry(catalogo, mock(FinalizarPartidaUseCase.class), new SnapshotMapper(), broadcaster);
        registro.cargarCatalogoYArrancarRuntimes();
    }

    @AfterEach
    void apagar() {
        registro.apagarScheduler();
    }

    @Test
    void listaLasSalasDelCatalogo_ordenadasPorCodigo() {
        assertThat(registro.listarSalas()).extracting(ResumenSala::codigo).containsExactly("SALA-01", "SALA-02");
        assertThat(registro.obtenerSala("s1").capacidad()).isEqualTo(3);
        assertThat(registro.obtenerSala("no-existe")).isNull();
    }

    @Test
    void entrarAOtraSala_sacaAlJugadorDeLaAnterior_yAvisaAlLobby() {
        registro.unirse("s1", "j1", "Ana");
        registro.unirse("s2", "j1", "Ana");

        assertThat(registro.obtenerSala("s1").cantidadJugadores()).isZero();
        assertThat(registro.obtenerSala("s2").idsJugadores()).containsExactly("j1");
        assertThat(registro.jugadoresEnEspera()).containsOnlyKeys("s2");
        verify(broadcaster, atLeastOnce()).difundirSalas(anyList());
    }

    @Test
    void enrutaCadaOperacionASuSala() {
        registro.unirse("s1", "j1", "Ana");
        registro.encolarMovimiento("s1", "j1", Direccion.ARRIBA);
        registro.pausar("s1", "j1");
        registro.reanudar("s1", "j1");
        registro.marcarReconectado("s1", "j1");
        registro.marcarDesconectado("s1", "j1");
        assertThat(registro.obtenerSala("s1").cantidadJugadores()).isZero();

        registro.unirse("s1", "j2", "Beto");
        registro.salir("s1", "j2");
        assertThat(registro.jugadoresEnEspera()).isEmpty();
    }

    @Test
    void conUnaSalaQueNoExiste_lasOperacionesDeMejorEsfuerzoSeIgnoran_yLasDemasFallan() {
        registro.encolarMovimiento("x", "j1", Direccion.ARRIBA);
        registro.marcarDesconectado("x", "j1");
        registro.pausar("x", "j1");
        registro.reanudar("x", "j1");
        assertThrows(SalaNoEncontradaException.class, () -> registro.unirse("x", "j1", "Ana"));
        assertThrows(SalaNoEncontradaException.class, () -> registro.salir("x", "j1"));
        assertThrows(SalaNoEncontradaException.class, () -> registro.marcarReconectado("x", "j1"));
    }

    @Test
    void apagarSinHaberCargado_noFalla() {
        new SalaRuntimeRegistry(catalogo, mock(FinalizarPartidaUseCase.class), new SnapshotMapper(), broadcaster).apagarScheduler();
    }
}
