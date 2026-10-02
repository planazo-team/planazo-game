package com.planazo.minijuego.domain.port.out;

import com.planazo.minijuego.domain.model.DefinicionSala;

import java.util.List;

/**
 * El catálogo de salas físicas pre-existentes (SNK-01/02/03: cupo fijo, no
 * creadas por el usuario). Fail-hard: Postgres es la fuente de verdad de
 * qué salas existen y con qué capacidad.
 */
public interface SalaCatalogoPort {

    List<DefinicionSala> listarTodas();
}
