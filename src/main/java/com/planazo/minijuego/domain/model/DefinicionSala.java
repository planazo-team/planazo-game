package com.planazo.minijuego.domain.model;

/**
 * Fila del catálogo de salas pre-existentes (decisión de producto: las
 * salas no las crea el usuario, existen de antemano con cupo fijo). Es la
 * "plantilla" a partir de la cual {@code SalaRuntimeRegistry} construye un
 * {@link Sala} en memoria al arrancar el servicio.
 */
public record DefinicionSala(String id, String codigo, int capacidad) {
}
