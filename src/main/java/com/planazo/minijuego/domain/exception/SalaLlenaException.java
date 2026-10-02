package com.planazo.minijuego.domain.exception;

/**
 * Se intentó unir un jugador a una sala que ya alcanzó su cupo fijo.
 * Satisface SNK-03 ("Ver un rechazo claro si la sala ya se llenó"): el
 * mensaje está pensado para mostrarse directo al jugador, no es un detalle
 * de log.
 */
public class SalaLlenaException extends DomainException {

    public SalaLlenaException(String salaCodigo, int capacidad) {
        super("SALA_LLENA", "La sala " + salaCodigo + " ya tiene sus " + capacidad + " cupos ocupados.");
    }
}
