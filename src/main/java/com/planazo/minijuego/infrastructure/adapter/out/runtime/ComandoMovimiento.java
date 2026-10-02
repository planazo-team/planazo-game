package com.planazo.minijuego.infrastructure.adapter.out.runtime;

import com.planazo.minijuego.domain.model.Direccion;

/**
 * Un cambio de dirección pedido por un jugador, en tránsito hacia el hilo
 * del game loop. Record inmutable: se crea en el hilo de WebSocket que
 * recibe el frame STOMP y se lee en el hilo del game loop — pasar un
 * objeto inmutable entre hilos por una cola concurrente es, junto con la
 * inmutabilidad de los DTOs, la forma más simple de no necesitar ningún
 * lock para esta ruta de datos.
 */
public record ComandoMovimiento(String jugadorId, Direccion direccion) {
}
