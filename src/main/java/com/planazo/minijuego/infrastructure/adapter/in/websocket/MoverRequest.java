package com.planazo.minijuego.infrastructure.adapter.in.websocket;

/** Payload de {@code /app/salas/{salaId}/mover}. {@code direccion} es uno de ARRIBA/ABAJO/IZQUIERDA/DERECHA. */
public record MoverRequest(String direccion) {
}
