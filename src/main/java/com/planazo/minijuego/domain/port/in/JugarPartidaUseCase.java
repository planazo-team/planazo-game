package com.planazo.minijuego.domain.port.in;

import java.util.List;
import java.util.Map;

import com.planazo.minijuego.domain.model.Direccion;

/**
 * SNK-04 (mover), SNK-07 (desconexión/reconexión con congelamiento). El
 * game loop en sí (el tick) no está aquí: es una decisión de
 * infraestructura (a qué ritmo, en qué hilo) que vive en
 * {@code infrastructure.adapter.out.runtime.SalaRuntime}, no un caso de
 * uso que alguien invoque desde afuera.
 */
public interface JugarPartidaUseCase {

    void moverSerpiente(String salaId, String jugadorId, Direccion direccion);

    void manejarDesconexion(String salaId, String jugadorId);

    void manejarReconexion(String salaId, String jugadorId);

    /** El jugador salió de la pantalla (otra pestaña u otra app): su serpiente se pausa. */
    void pausarSerpiente(String salaId, String jugadorId);

    /** El jugador volvió a la pantalla: su serpiente retoma donde iba. */
    void reanudarSerpiente(String salaId, String jugadorId);

    /** Quiénes están en salas que aún no arrancan: salaId → jugadorIds. */
    Map<String, List<String>> jugadoresEnSalaDeEspera();

    /** Saca de la sala a un jugador que se fue sin avisar (sin conexión de juego). */
    void retirarJugadorInactivo(String salaId, String jugadorId);
}
