package com.planazo.minijuego.domain.exception;

/**
 * Raíz de todas las excepciones de negocio del Minijuego. Cada subclase
 * lleva un {@code codigo} estable (pensado para que el cliente del juego lo
 * pueda usar programáticamente, no solo mostrar el mensaje) que
 * {@code GlobalExceptionHandler} traduce a un status HTTP.
 */
public abstract class DomainException extends RuntimeException {

    private final String codigo;

    protected DomainException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
