package com.planazo.minijuego.domain.exception;

/**
 * Se intentó entrar a una sala cuya partida ya está corriendo: nadie
 * aparece a mitad de una partida — hay que esperar a que termine y
 * entrar para la siguiente.
 */
public class PartidaYaIniciadaException extends DomainException {

    public PartidaYaIniciadaException(String salaCodigo) {
        super("PARTIDA_YA_INICIADA", "La sala " + salaCodigo + " tiene una partida en curso. Espera a que termine para entrar.");
    }
}
