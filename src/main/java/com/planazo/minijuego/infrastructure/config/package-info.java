/**
 * Configuración de Spring: nada de esto es una regla de negocio, es
 * exclusivamente "cómo se conecta este servicio con el mundo" (OpenAPI,
 * WebSocket/STOMP, Redis, RabbitMQ). Si una clase de aquí empieza a tomar
 * decisiones sobre el juego (cuántos jugadores caben, cuándo se gana),
 * esa lógica se mudó al lugar equivocado — pertenece a {@code domain}.
 */
package com.planazo.minijuego.infrastructure.config;
