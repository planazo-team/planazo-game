package com.planazo.minijuego.domain.port.in;

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
}
