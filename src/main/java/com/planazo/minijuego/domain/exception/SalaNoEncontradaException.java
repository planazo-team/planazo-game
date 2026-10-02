package com.planazo.minijuego.domain.exception;

public class SalaNoEncontradaException extends DomainException {

    public SalaNoEncontradaException(String salaId) {
        super("SALA_NO_ENCONTRADA", "No existe una sala con identificador " + salaId + ".");
    }
}
