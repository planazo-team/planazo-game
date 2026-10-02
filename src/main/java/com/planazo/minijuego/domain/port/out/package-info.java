/**
 * Lo que el dominio necesita del exterior, declarado como interfaz desde
 * adentro hacia afuera (Dependency Inversion / puerto de salida). Cada uno
 * se implementa en {@code infrastructure.adapter.out.*}, y cada
 * implementación decide su propia postura ante fallas:
 * {@code SalaCatalogoPort} y {@code ResultadoPartidaRepositoryPort} son
 * "fail-hard" (Postgres es la fuente de verdad: si no responde, la
 * operación falla) y {@code LeaderboardPort} / {@code EventPublisherPort}
 * son "fail-soft" (Redis y RabbitMQ son mejoras de experiencia: si no
 * responden, el juego sigue jugándose).
 */
package com.planazo.minijuego.domain.port.out;
