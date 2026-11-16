package com.planazo.minijuego.infrastructure.adapter.in.rest.dto;

import java.util.List;

/**
 * {@code jugadorId} debe ser uno de los usuarios de prueba pre-seedeados
 * (ver GET /api/v1/auth/usuarios-de-prueba). {@code roles} es opcional —
 * si se omite, el token queda con {@code ["jugador"]} — y solo sirve para
 * probar autorización por rol en local (ver {@code SecurityConfig}).
 */
public record DevTokenRequest(String jugadorId, List<String> roles) {
    public DevTokenRequest(String jugadorId) {
        this(jugadorId, null);
    }
}
