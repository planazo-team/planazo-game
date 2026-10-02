package com.planazo.minijuego.domain.exception;

/**
 * Se intentó mover una serpiente en una sala cuya partida todavía no
 * empezó o ya terminó. No es un error grave del cliente (puede pasar por
 * simple latencia de red), así que se mapea a un 409, no a un 400.
 */
public class PartidaNoEnCursoException extends DomainException {

    public PartidaNoEnCursoException(String salaId) {
        super("PARTIDA_NO_EN_CURSO", "La sala " + salaId + " no tiene una partida en curso en este momento.");
    }
}
