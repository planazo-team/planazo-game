package com.planazo.minijuego.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila del catálogo de salas físicas pre-existentes. Es, a propósito, una
 * tabla casi de solo-lectura desde este servicio: se puebla por
 * migración ({@code V1__catalogo_salas.sql}) y no por un endpoint REST —
 * las salas no las crea el usuario (decisión de producto explícita).
 */
@Entity
@Table(name = "salas")
public class SalaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 64)
    private String id;

    @Column(name = "codigo", nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(name = "capacidad", nullable = false)
    private int capacidad;

    protected SalaEntity() {
        // Requerido por JPA.
    }

    public SalaEntity(String id, String codigo, int capacidad) {
        this.id = id;
        this.codigo = codigo;
        this.capacidad = capacidad;
    }

    public String getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public int getCapacidad() {
        return capacidad;
    }
}
