package com.planazo.minijuego.domain.model;

/**
 * Línea del marcador final de una partida para un solo jugador. Objeto de
 * valor inmutable: una vez la partida termina, esta fila no vuelve a
 * cambiar — es historia.
 */
public record PuntajeJugador(
        String jugadorId,
        String jugadorNombre,
        int puntaje,
        int posicionFinal,
        boolean sobrevivio
) {
}
