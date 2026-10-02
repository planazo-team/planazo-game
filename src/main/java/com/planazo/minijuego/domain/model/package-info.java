/**
 * Modelo de dominio puro del microservicio Minijuego (Snake multijugador).
 *
 * <p>Nada en este paquete importa Spring, JPA, Redis ni ningún framework.
 * Estas clases son las únicas que saben qué significa "mover una serpiente"
 * o "quién ganó la partida" — esa regla de negocio no depende de si el
 * transporte es WebSocket o si el resultado se guarda en Postgres o en un
 * archivo de texto.
 *
 * <p>Dos clases concentran casi toda la lógica interesante:
 * <ul>
 *   <li>{@link com.planazo.minijuego.domain.model.Sala} — el agregado raíz.
 *       Es la única clase mutable de este paquete con estado compartido, y
 *       por diseño se accede desde un único hilo a la vez (ver
 *       {@code infrastructure.adapter.out.runtime.SalaRuntime}). No tiene
 *       ningún {@code synchronized} propio porque no lo necesita: el
 *       aislamiento de hilos se garantiza por fuera, no por dentro.</li>
 *   <li>{@link com.planazo.minijuego.domain.model.Serpiente} — el estado de
 *       un jugador dentro de una sala (cuerpo, dirección, puntaje, vivo o
 *       eliminado).</li>
 * </ul>
 *
 * <p>Todo lo demás en este paquete ({@link com.planazo.minijuego.domain.model.Posicion},
 * {@link com.planazo.minijuego.domain.model.Direccion},
 * {@link com.planazo.minijuego.domain.model.EstadoSerpiente},
 * {@link com.planazo.minijuego.domain.model.EstadoSala},
 * {@link com.planazo.minijuego.domain.model.ResultadoPartida}) son objetos de
 * valor inmutables ({@code record} o {@code enum}) — se pueden compartir
 * libremente entre hilos sin ningún riesgo, porque no se pueden modificar
 * después de construidos.
 */
package com.planazo.minijuego.domain.model;
