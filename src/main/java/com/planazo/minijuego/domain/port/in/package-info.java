/**
 * Casos de uso del Minijuego, vistos desde afuera: lo que un controlador
 * REST o un handler de WebSocket puede pedirle al dominio. Son interfaces a
 * propósito — principio de abstracción de servicios: quien llama no sabe
 * (ni debe saber) si detrás hay un {@code SalaRuntimeRegistry} en memoria,
 * Redis, o ambos.
 */
package com.planazo.minijuego.domain.port.in;
