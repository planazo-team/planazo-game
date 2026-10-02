package com.planazo.minijuego.infrastructure.adapter.in.rest;

/** Envoltura estándar de toda respuesta exitosa de este servicio. */
public record ApiResponse<T>(T data) {
    public static <T> ApiResponse<T> de(T data) {
        return new ApiResponse<>(data);
    }
}
