package com.planazo.minijuego.domain.model;

/**
 * Ciclo de vida de una sala.
 *
 * <pre>
 * ESPERANDO_JUGADORES --(sala llena)--&gt; CUENTA_REGRESIVA --(3 s)--&gt; EN_CURSO
 *        ^                       |                                      |
 *        +--(alguien sale)-------+                                      |
 *        +--(se difunde el resultado y la sala queda vacía)------------ FINALIZADA &lt;-+
 * </pre>
 *
 * <p>Las salas son pre-existentes y de cupo fijo (decisión de producto: no
 * se crean salas dinámicamente). Por eso este enum no tiene un estado
 * "eliminada" — tras {@code FINALIZADA} la sala vuelve a
 * {@code ESPERANDO_JUGADORES} vacía: no hay revancha automática, quien
 * quiera seguir jugando vuelve a entrar.
 */
public enum EstadoSala {
    ESPERANDO_JUGADORES,
    CUENTA_REGRESIVA,
    EN_CURSO,
    FINALIZADA
}
