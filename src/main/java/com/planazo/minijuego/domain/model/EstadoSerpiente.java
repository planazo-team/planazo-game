package com.planazo.minijuego.domain.model;

/**
 * Estado de un jugador dentro de una partida en curso.
 *
 * <p>{@code DESCONECTADA_CONGELADA} es el estado que implementa la decisión
 * de producto "se congela unos segundos por si reconecta": al perder el
 * socket, la serpiente no se elimina ni sigue moviéndose sola — se congela
 * en su lugar (sigue ocupando espacio, sigue siendo un obstáculo para las
 * demás) durante una ventana de gracia. Si el jugador reconecta dentro de
 * esa ventana, vuelve a {@code VIVA} sin perder nada. Si se agota, pasa a
 * {@code ELIMINADA}.
 */
public enum EstadoSerpiente {
    VIVA,
    DESCONECTADA_CONGELADA,
    ELIMINADA
}
