package com.planazo.minijuego.infrastructure.adapter.in.rest.dto;

/** {@code jugadorId} debe ser uno de los usuarios de prueba pre-seedeados (ver GET /api/v1/auth/usuarios-de-prueba). */
public record DevTokenRequest(String jugadorId) {
}
