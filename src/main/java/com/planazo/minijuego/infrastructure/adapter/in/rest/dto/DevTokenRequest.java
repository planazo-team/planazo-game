package com.planazo.minijuego.infrastructure.adapter.in.rest.dto;

import java.util.List;

/**
 * {@code jugadorId} debe ser uno de los usuarios de prueba pre-seedeados (ver GET /api/v1/auth/usuarios-de-prueba).
 * {@code roles} es opcional (infraestructura para autorización por rol, sin roles de negocio reales
 * todavía): si se omite, el token sale con el rol por defecto {@code jugador}.
 */
public record DevTokenRequest(String jugadorId, List<String> roles) {
}
