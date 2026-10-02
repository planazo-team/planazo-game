/**
 * Sobres de evento (EDA) que este servicio publica. Cada evento va
 * envuelto en {@link com.planazo.minijuego.infrastructure.adapter.out.messaging.event.DomainEvent}
 * con sus metadatos estándar de trazabilidad — nunca se publica un
 * payload suelto. Este microservicio, en esta primera pasada, solo
 * publica eventos (no consume ninguno todavía), así que no hay tabla
 * {@code processed_events} de idempotencia: esa pieza se agrega el día
 * que este servicio empiece a consumir algo de otro microservicio de
 * Planazo, siguiendo el mismo patrón que {@code mapa-descubrimiento-service}.
 */
package com.planazo.minijuego.infrastructure.adapter.out.messaging.event;
