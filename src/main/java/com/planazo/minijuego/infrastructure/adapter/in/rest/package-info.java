/**
 * Controladores REST versionados ({@code /api/v1/...}). Siguen las
 * buenas prácticas obligatorias del servicio: recursos en sustantivo
 * plural, sub-recursos para jerarquía ({@code /salas/{id}/jugadores}),
 * {@code GET} nunca muta, y códigos HTTP estándar vía
 * {@link com.planazo.minijuego.infrastructure.adapter.in.rest.GlobalExceptionHandler}.
 * Todas las respuestas se envuelven en
 * {@link com.planazo.minijuego.infrastructure.adapter.in.rest.ApiResponse}
 * para que el cliente siempre sepa dónde buscar el dato real y dónde el
 * error, sin adivinar por el shape de cada endpoint.
 */
package com.planazo.minijuego.infrastructure.adapter.in.rest;
