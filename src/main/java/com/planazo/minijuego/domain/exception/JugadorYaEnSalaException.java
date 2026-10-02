package com.planazo.minijuego.domain.exception;

public class JugadorYaEnSalaException extends DomainException {

    public JugadorYaEnSalaException(String jugadorId) {
        super("JUGADOR_YA_EN_SALA", "El jugador " + jugadorId + " ya está en esta sala.");
    }
}
