package com.planazo.minijuego.domain.model;

/**
 * Por qué terminó la partida con este ganador — condición de victoria
 * híbrida decidida con el equipo: "último en pie, o el de mayor puntaje si
 * el tiempo se agota antes".
 */
public enum MotivoVictoria {
    /** Quedó exactamente un jugador vivo; los demás chocaron o salieron. */
    ULTIMO_EN_PIE,
    /** Se agotó el tiempo límite con 2 o más jugadores vivos; gana el mayor puntaje. */
    MAYOR_PUNTAJE_POR_TIEMPO,
    /** Dos o más serpientes chocaron en el mismo tick y no quedó ninguna viva; gana el mayor puntaje entre ellas. */
    EMPATE_TODOS_ELIMINADOS,
    /** La sala se vació sin que nadie llegara a jugar (caso borde, no debería verse en la práctica). */
    EMPATE_SIN_JUGADORES
}
