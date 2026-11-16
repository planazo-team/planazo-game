package com.planazo.minijuego.infrastructure.adapter.in.websocket;

import com.planazo.minijuego.domain.model.Direccion;
import com.planazo.minijuego.domain.port.in.JugarPartidaUseCase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quien entra a una sala y se va sin conexión de juego (cerró la app antes de
 * registrar su sesión STOMP) no puede quedarse ocupando el cupo para siempre.
 */
class LimpiadorDeSalasTest {

    /** Caso de uso falso: salas en espera en memoria y registro de quién fue retirado. */
    static final class SalasFalsas implements JugarPartidaUseCase {
        final Map<String, List<String>> enEspera = new HashMap<>();
        final List<String> retirados = new ArrayList<>();

        @Override public void moverSerpiente(String salaId, String jugadorId, Direccion direccion) {}
        @Override public void manejarDesconexion(String salaId, String jugadorId) {}
        @Override public void manejarReconexion(String salaId, String jugadorId) {}
        @Override public void pausarSerpiente(String salaId, String jugadorId) {}
        @Override public void reanudarSerpiente(String salaId, String jugadorId) {}
        @Override public Map<String, List<String>> jugadoresEnSalaDeEspera() { return enEspera; }

        @Override
        public void retirarJugadorInactivo(String salaId, String jugadorId) {
            retirados.add(salaId + "/" + jugadorId);
            enEspera.get(salaId).remove(jugadorId);
        }
    }

    @Test
    void sinSesion_masDeLaGracia_esRetirado() {
        SalasFalsas salas = new SalasFalsas();
        salas.enEspera.put("sala-005", new ArrayList<>(List.of("c-ana")));
        LimpiadorDeSalas limpiador = new LimpiadorDeSalas(salas, new SesionJuegoRegistry(), 20);

        limpiador.revisar(0);
        limpiador.revisar(10_000);
        assertThat(salas.retirados).isEmpty(); // aún dentro de la gracia

        limpiador.revisar(20_000);
        assertThat(salas.retirados).containsExactly("sala-005/c-ana");
    }

    @Test
    void conSesionRegistrada_nuncaEsRetirado() {
        SalasFalsas salas = new SalasFalsas();
        salas.enEspera.put("sala-005", new ArrayList<>(List.of("c-ana")));
        SesionJuegoRegistry sesiones = new SesionJuegoRegistry();
        sesiones.registrar("s1", "sala-005", "c-ana");
        LimpiadorDeSalas limpiador = new LimpiadorDeSalas(salas, sesiones, 20);

        limpiador.revisar(0);
        limpiador.revisar(60_000);

        assertThat(salas.retirados).isEmpty();
    }

    @Test
    void siRecuperaLaSesion_seReiniciaLaCuenta() {
        SalasFalsas salas = new SalasFalsas();
        salas.enEspera.put("sala-005", new ArrayList<>(List.of("c-ana")));
        SesionJuegoRegistry sesiones = new SesionJuegoRegistry();
        LimpiadorDeSalas limpiador = new LimpiadorDeSalas(salas, sesiones, 20);

        limpiador.revisar(0);
        sesiones.registrar("s1", "sala-005", "c-ana");
        limpiador.revisar(15_000);
        sesiones.remover("s1");
        limpiador.revisar(16_000); // vuelve a quedar sin sesión: cuenta desde aquí
        limpiador.revisar(30_000);
        assertThat(salas.retirados).isEmpty();

        limpiador.revisar(36_000);
        assertThat(salas.retirados).containsExactly("sala-005/c-ana");
    }
}
