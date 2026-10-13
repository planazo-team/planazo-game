package com.planazo.minijuego.application.dto;

import com.planazo.minijuego.domain.model.Posicion;

import java.util.List;

/**
 * Snapshot de una serpiente en un instante dado, listo para difundirse por
 * WebSocket (RT-3). Se reconstruye en cada tick a partir de
 * {@code Serpiente} — nunca se guarda ni se reutiliza entre ticks, así que
 * no hay ningún problema de estado compartido al pasarlo entre hilos
 * (el publisher de Spring lo serializa en el mismo tick en que se crea).
 */
public record JugadorEnSalaDTO(
        String jugadorId,
        String jugadorNombre,
        List<Posicion> cuerpo,
        String direccion,
        String estado,
        int puntaje,
        boolean conectado,
        /** Salió de la pantalla: su serpiente está quieta esperándolo. */
        boolean pausado
) {
}
