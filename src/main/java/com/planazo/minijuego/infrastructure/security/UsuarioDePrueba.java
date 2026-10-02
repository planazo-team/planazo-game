package com.planazo.minijuego.infrastructure.security;

import java.util.List;

/**
 * Los usuarios "quemados" (pre-seedeados) decididos con el equipo: en el
 * MVP no hay auto-registro para jugar Snake, hay una lista fija de
 * usuarios reales contra la que se autentica. En producción estos
 * existirían en el microservicio de Registro y autenticación; aquí, para
 * poder probar este servicio solo, viven como una lista estática.
 */
public record UsuarioDePrueba(String jugadorId, String nombre) {

    public static final List<UsuarioDePrueba> TODOS = List.of(
            new UsuarioDePrueba("j-ana", "Ana"),
            new UsuarioDePrueba("j-bruno", "Bruno"),
            new UsuarioDePrueba("j-camila", "Camila"),
            new UsuarioDePrueba("j-diego", "Diego"),
            new UsuarioDePrueba("j-elena", "Elena"),
            new UsuarioDePrueba("j-fabian", "Fabián")
    );

    public static UsuarioDePrueba buscar(String jugadorId) {
        return TODOS.stream()
                .filter(u -> u.jugadorId().equalsIgnoreCase(jugadorId))
                .findFirst()
                .orElse(null);
    }
}
