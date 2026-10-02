/**
 * El "motor" del juego: dónde viven las salas en memoria y corre el game
 * loop en tiempo real (RT-3). Esta es la parte de infraestructura más
 * atípica del servicio — no persiste nada por sí misma, administra estado
 * mutable concurrente a propósito, con reglas muy explícitas:
 *
 * <ul>
 *   <li>{@link com.planazo.minijuego.infrastructure.adapter.out.runtime.SalaRuntime}
 *       es el "actor" de una sola sala: un {@code ReentrantLock} propio
 *       protege toda mutación del agregado {@code Sala} (unirse, salir,
 *       desconexión/reconexión y el propio tick), y una
 *       {@code ConcurrentLinkedQueue} sin lock recibe los comandos de
 *       movimiento desde cualquier hilo de WebSocket — el hilo del game
 *       loop es el único que los drena, en su propio tick.</li>
 *   <li>{@link com.planazo.minijuego.infrastructure.adapter.out.runtime.SalaRuntimeRegistry}
 *       es el {@code GestorDeSalasEnVivoPort}: crea un {@code SalaRuntime}
 *       por cada fila del catálogo al arrancar, y enruta cada operación
 *       del caso de uso hacia el runtime correcto.</li>
 * </ul>
 *
 * <p>Nunca hay más de un lock involucrado a la vez y siempre es el mismo
 * (el de la sala en cuestión) — cero riesgo de deadlock por orden cruzado,
 * como exige la regla de concurrencia 4 del servicio hermano
 * {@code mapa-descubrimiento-service/ARQUITECTURA.md} (replicada aquí en
 * este mismo README).
 */
package com.planazo.minijuego.infrastructure.adapter.out.runtime;
