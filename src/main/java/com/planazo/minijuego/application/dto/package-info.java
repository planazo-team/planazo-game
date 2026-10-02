/**
 * Formas de datos que le pertenecen al caso de uso, no al transporte. La
 * diferencia con {@code infrastructure.adapter.in.rest.dto} es sutil pero
 * importante: estos DTOs los produce {@code application.service} a partir
 * del dominio (por ejemplo, un snapshot de la partida para difundir por
 * WebSocket) y son los mismos sin importar si el cliente es REST, STOMP o
 * una prueba unitaria. Los DTOs de REST, en cambio, existen solo para tener
 * anotaciones de OpenAPI/Jackson específicas de esa vía de entrada.
 */
package com.planazo.minijuego.application.dto;
