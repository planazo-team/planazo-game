package com.planazo.minijuego.domain.model;

/**
 * Dirección de movimiento de una serpiente en la grilla del tablero.
 *
 * <p>La grilla usa el origen (0,0) en la esquina superior izquierda, con el
 * eje Y creciendo hacia abajo (convención estándar de UI/canvas, para que el
 * cliente no tenga que invertir nada al dibujar).
 */
public enum Direccion {
    ARRIBA(0, -1),
    ABAJO(0, 1),
    IZQUIERDA(-1, 0),
    DERECHA(1, 0);

    private final int deltaX;
    private final int deltaY;

    Direccion(int deltaX, int deltaY) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    public int deltaX() {
        return deltaX;
    }

    public int deltaY() {
        return deltaY;
    }

    /**
     * Una serpiente nunca puede invertir su dirección sobre sí misma en un
     * solo tick (moverse a la derecha y al toque a la izquierda la haría
     * chocar consigo misma de forma artificial e injusta). Esta regla se usa
     * en {@link Serpiente#cambiarDireccion(Direccion)} para descartar
     * comandos de movimiento inválidos en silencio, no para rechazarlos con
     * un error — es una entrada de usuario normal, no una excepción.
     */
    public boolean esOpuesta(Direccion otra) {
        return this.deltaX + otra.deltaX == 0 && this.deltaY + otra.deltaY == 0;
    }
}
