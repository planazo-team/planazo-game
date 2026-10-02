package com.planazo.minijuego.infrastructure.adapter.in.rest;

import java.time.Instant;

/** Envoltura estándar de toda respuesta de error de este servicio. */
public record ApiError(String codigo, String mensaje, Instant timestamp) {
    public static ApiError de(String codigo, String mensaje) {
        return new ApiError(codigo, mensaje, Instant.now());
    }
}
