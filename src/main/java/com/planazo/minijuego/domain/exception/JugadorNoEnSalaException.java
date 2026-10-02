package com.planazo.minijuego.domain.exception;

public class JugadorNoEnSalaException extends DomainException {

    public JugadorNoEnSalaException(String jugadorId) {
        super("JUGADOR_NO_EN_SALA", "El jugador " + jugadorId + " no está en esta sala.");
    }
}
