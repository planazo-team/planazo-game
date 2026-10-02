package com.planazo.minijuego.domain.model;

/**
 * Una fila del leaderboard global (SNK-08). Vive en el dominio, no en
 * {@code application.dto}, porque representar "una entrada del marcador"
 * es una regla del negocio del juego, no un detalle de cómo se serializa
 * la respuesta HTTP — lo segundo sí es trabajo de
 * {@code infrastructure.adapter.in.rest.dto}.
 */
public record EntradaLeaderboard(int posicion, String jugadorId, String jugadorNombre, long puntajeTotal) {
}
