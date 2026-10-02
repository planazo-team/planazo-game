package com.planazo.minijuego.infrastructure.adapter.in.rest.dto;

public record DevTokenResponse(String token, String tipo, String jugadorId, String jugadorNombre) {
    public static DevTokenResponse de(String token, String jugadorId, String jugadorNombre) {
        return new DevTokenResponse(token, "Bearer", jugadorId, jugadorNombre);
    }
}
