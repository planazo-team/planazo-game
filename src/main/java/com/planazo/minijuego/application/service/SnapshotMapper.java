package com.planazo.minijuego.application.service;

import com.planazo.minijuego.application.dto.EstadoJuegoDTO;
import com.planazo.minijuego.application.dto.JugadorEnSalaDTO;
import com.planazo.minijuego.domain.model.Sala;
import com.planazo.minijuego.domain.model.Serpiente;
import com.planazo.minijuego.domain.model.ResultadoPartida;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Convierte el estado mutable de un {@link Sala} en el snapshot inmutable
 * que se difunde por WebSocket (RT-3). Vive en {@code application} — y no
 * en el adaptador de WebSocket ni en el runtime — porque "qué parte del
 * estado del juego le importa al cliente" es una decisión del caso de uso,
 * no del transporte. {@code infrastructure.adapter.out.runtime.SalaRuntime}
 * la llama después de cada tick.
 */
@Component
public class SnapshotMapper {

    public EstadoJuegoDTO aEstadoJuegoDTO(Sala sala, ResultadoPartida resultadoSiTermino) {
        List<JugadorEnSalaDTO> jugadores = sala.serpientes().stream()
                .map(this::aJugadorDTO)
                .toList();

        return new EstadoJuegoDTO(
                sala.id(),
                sala.codigo(),
                sala.estado().name(),
                sala.capacidad(),
                sala.segundosCuentaRegresiva(),
                Sala.ANCHO_TABLERO,
                Sala.ALTO_TABLERO,
                jugadores,
                sala.comida(),
                sala.tiempoRestanteSegundos(),
                resultadoSiTermino
        );
    }

    private JugadorEnSalaDTO aJugadorDTO(Serpiente serpiente) {
        return new JugadorEnSalaDTO(
                serpiente.jugadorId(),
                serpiente.jugadorNombre(),
                serpiente.cuerpoInmutable(),
                serpiente.direccion().name(),
                serpiente.estado().name(),
                serpiente.puntaje(),
                serpiente.estaConectado()
        );
    }
}
