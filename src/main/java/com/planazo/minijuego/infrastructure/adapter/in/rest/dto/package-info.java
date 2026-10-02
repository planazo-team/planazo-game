/**
 * DTOs específicos de la vía REST — existen solo por anotaciones de
 * OpenAPI/Jackson particulares de esta entrada, no porque el dominio o
 * la aplicación los necesiten. La mayoría de las respuestas de este
 * servicio en realidad devuelven directamente objetos de
 * {@code domain.model} (como {@code ResumenSala} o
 * {@code EntradaLeaderboard}) envueltos en {@code ApiResponse} — son
 * value objects inmutables sin ninguna dependencia de framework, así que
 * no hace falta duplicarlos aquí. Este paquete es para lo que sí es
 * puramente de transporte, como el intercambio del token de desarrollo.
 */
package com.planazo.minijuego.infrastructure.adapter.in.rest.dto;
