package com.planazo.minijuego.infrastructure.adapter.out.runtime;

import com.planazo.minijuego.application.dto.EstadoJuegoDTO;
import com.planazo.minijuego.domain.model.ResumenSala;

import java.util.List;

/**
 * Lo que {@link SalaRuntime} necesita para avisarle al mundo exterior que
 * el estado de una sala cambió. Es una interfaz definida por quien la
 * consume (el runtime), no por quien la implementa — por eso vive en este
 * paquete y no en {@code infrastructure.adapter.in.websocket}, aunque la
 * implementación real ({@code StompGameBroadcaster}) esté allá. Mismo
 * patrón de inversión de dependencias que un puerto de dominio, aplicado
 * entre dos adaptadores de infraestructura.
 */
public interface GameBroadcaster {

    void difundirEstado(EstadoJuegoDTO estado);

    /** Lista de salas para el lobby: cupo y estado en vivo, sin tener que refrescar (SNK-01/02). */
    void difundirSalas(List<ResumenSala> salas);
}
