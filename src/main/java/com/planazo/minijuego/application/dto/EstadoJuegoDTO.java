package com.planazo.minijuego.application.dto;

import com.planazo.minijuego.domain.model.Posicion;
import com.planazo.minijuego.domain.model.ResultadoPartida;

import java.util.List;

/**
 * El mensaje que se publica en {@code /topic/salas/{salaId}} en cada tick
 * (RT-3). Cuando {@code estado} es {@code FINALIZADA}, {@code resultado}
 * viene poblado (SNK-10: "ver quién ganó al terminar la partida") — el
 * cliente no necesita hacer una llamada REST aparte para saber el
 * ganador, le llega en el mismo canal que ya estaba escuchando.
 */
public record EstadoJuegoDTO(
        String salaId,
        String salaCodigo,
        String estado,
        int capacidad,
        long segundosCuentaRegresiva,
        int anchoTablero,
        int altoTablero,
        List<JugadorEnSalaDTO> jugadores,
        Posicion comida,
        long tiempoRestanteSegundos,
        ResultadoPartida resultado
) {
}
