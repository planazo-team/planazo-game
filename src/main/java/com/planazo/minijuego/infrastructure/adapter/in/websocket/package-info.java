/**
 * Puerto de entrada en tiempo real (RT-3): STOMP sobre WebSocket/SockJS.
 * Flujo esperado del cliente: (1) unirse a la sala por REST
 * ({@code GestionarSalasUseCase}, en {@code adapter.in.rest}); (2) abrir
 * la conexión STOMP con el mismo JWT; (3) mandar
 * {@code /app/salas/{salaId}/registrar-sesion} para que este servicio
 * sepa asociar esa sesión de WebSocket con esa sala y jugador — necesario
 * para poder reaccionar a una desconexión (SNK-07); (4) suscribirse a
 * {@code /topic/salas/{salaId}} para recibir el estado de cada tick; (5)
 * esperar: la partida arranca sola tras una cuenta regresiva cuando la
 * sala se llena; (6) mandar {@code /app/salas/{salaId}/mover} en cada
 * tecla — cada comando mueve la serpiente una celda. El lobby
 * se escucha en {@code /topic/salas} y los errores propios en
 * {@code /user/queue/errores}.
 */
package com.planazo.minijuego.infrastructure.adapter.in.websocket;
