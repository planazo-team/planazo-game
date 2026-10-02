package com.planazo.minijuego.domain.model;

import java.util.List;

/**
 * Vista de solo lectura de una sala, segura de pasar entre hilos (record
 * inmutable) — a diferencia de {@link Sala}, que solo su
 * {@code SalaRuntime} dueño puede tocar. Cualquier respuesta REST que
 * hable de "la sala" usa esto, nunca el agregado {@link Sala} en vivo:
 * exponer la referencia mutable fuera de su hilo dueño sería exactamente
 * el tipo de estado compartido sin protección que las reglas de
 * concurrencia de este servicio prohíben.
 *
 * <p>Satisface SNK-01 (cupo disponible), SNK-02 ("ver quién más está en
 * la sala") y es la respuesta positiva de SNK-03.
 */
public record ResumenSala(
        String id,
        String codigo,
        int capacidad,
        int cantidadJugadores,
        String estado,
        List<String> nombresJugadores
) {
    public boolean tieneCupoDisponible() {
        return cantidadJugadores < capacidad;
    }
}
