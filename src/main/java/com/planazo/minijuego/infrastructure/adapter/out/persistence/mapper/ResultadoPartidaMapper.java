package com.planazo.minijuego.infrastructure.adapter.out.persistence.mapper;

import com.planazo.minijuego.domain.model.MotivoVictoria;
import com.planazo.minijuego.domain.model.PuntajeJugador;
import com.planazo.minijuego.domain.model.ResultadoPartida;
import com.planazo.minijuego.infrastructure.adapter.out.persistence.entity.PuntajeJugadorEntity;
import com.planazo.minijuego.infrastructure.adapter.out.persistence.entity.ResultadoPartidaEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Traduce entre el objeto de valor de dominio {@link ResultadoPartida} y
 * las entidades JPA. El dominio no sabe que existe una tabla
 * {@code puntajes_jugador} — solo sabe que un resultado tiene una lista de
 * {@link PuntajeJugador}.
 */
@Component
public class ResultadoPartidaMapper {

    public ResultadoPartidaEntity aEntidad(ResultadoPartida resultado) {
        ResultadoPartidaEntity entidad = new ResultadoPartidaEntity(
                resultado.salaId(),
                resultado.salaCodigo(),
                resultado.ganadorJugadorId(),
                resultado.ganadorJugadorNombre(),
                resultado.motivoVictoria().name(),
                resultado.iniciadaEn(),
                resultado.finalizadaEn()
        );
        for (PuntajeJugador puntaje : resultado.puntajes()) {
            entidad.agregarPuntaje(new PuntajeJugadorEntity(
                    puntaje.jugadorId(),
                    puntaje.jugadorNombre(),
                    puntaje.puntaje(),
                    puntaje.posicionFinal(),
                    puntaje.sobrevivio()
            ));
        }
        return entidad;
    }

    public ResultadoPartida aDominio(ResultadoPartidaEntity entidad) {
        List<PuntajeJugador> puntajes = entidad.getPuntajes().stream()
                .map(p -> new PuntajeJugador(p.getJugadorId(), p.getJugadorNombre(), p.getPuntaje(), p.getPosicionFinal(), p.isSobrevivio()))
                .toList();
        return new ResultadoPartida(
                entidad.getSalaId(),
                entidad.getSalaCodigo(),
                entidad.getGanadorJugadorId(),
                entidad.getGanadorJugadorNombre(),
                MotivoVictoria.valueOf(entidad.getMotivoVictoria()),
                puntajes,
                entidad.getIniciadaEn(),
                entidad.getFinalizadaEn()
        );
    }
}
