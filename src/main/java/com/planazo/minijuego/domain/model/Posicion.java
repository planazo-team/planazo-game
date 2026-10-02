package com.planazo.minijuego.domain.model;

/**
 * Coordenada inmutable dentro de la grilla del tablero. Objeto de valor: dos
 * posiciones con las mismas coordenadas son intercambiables, de ahí el
 * {@code record} — {@code equals}/{@code hashCode} correctos "gratis", algo
 * que la lógica de colisión necesita constantemente (¿esta posición ya está
 * ocupada por algún cuerpo?).
 */
public record Posicion(int x, int y) {

    public Posicion trasladar(Direccion direccion) {
        return new Posicion(x + direccion.deltaX(), y + direccion.deltaY());
    }

    public boolean dentroDe(int ancho, int alto) {
        return x >= 0 && x < ancho && y >= 0 && y < alto;
    }
}
