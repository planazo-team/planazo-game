/**
 * Adaptadores JPA/Postgres: la única fuente de verdad de este servicio
 * (fail-hard, a diferencia de Redis/RabbitMQ). Dos responsabilidades muy
 * distintas conviven aquí:
 *
 * <ul>
 *   <li>El catálogo de salas pre-existentes ({@code entity.SalaEntity},
 *       vía {@code JpaSalaCatalogoAdapter}) — datos de referencia, casi
 *       estáticos, cargados una vez al arrancar por
 *       {@code SalaRuntimeRegistry}.</li>
 *   <li>El historial de partidas terminadas
 *       ({@code entity.ResultadoPartidaEntity} +
 *       {@code entity.PuntajeJugadorEntity}, vía
 *       {@code JpaResultadoPartidaRepositoryAdapter}) — se escribe una vez
 *       por partida, nunca se actualiza (CC-2: "que mi puntaje quede
 *       registrado sin perderse").</li>
 * </ul>
 */
package com.planazo.minijuego.infrastructure.adapter.out.persistence;
