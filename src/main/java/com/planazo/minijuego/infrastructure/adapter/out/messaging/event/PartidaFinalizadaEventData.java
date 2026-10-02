package com.planazo.minijuego.infrastructure.adapter.out.messaging.event;

import java.time.Instant;
import java.util.List;

/**
 * Payload de {@code PartidaFinalizada} (hecho consumado, en pasado, sin
 * receptor predefinido — cualquier microservicio de Planazo que quiera
 * reaccionar a partidas terminadas, p.ej. un futuro servicio de logros,
 * puede suscribirse al exchange {@code planazo.events}).
 */
public record PartidaFinalizadaEventData(
        String salaId,
        String salaCodigo,
        String ganadorJugadorId,
        String ganadorJugadorNombre,
        String motivoVictoria,
        List<LineaPuntaje> puntajes,
        Instant iniciadaEn,
        Instant finalizadaEn
) {
    public record LineaPuntaje(String jugadorId, String jugadorNombre, int puntaje, int posicionFinal, boolean sobrevivio) {
    }
}
