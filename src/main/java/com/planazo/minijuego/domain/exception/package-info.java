/**
 * Excepciones del dominio del Minijuego. Todas extienden
 * {@link com.planazo.minijuego.domain.exception.DomainException}, que no
 * depende de Spring ni de HTTP — el mapeo a un código de estado concreto
 * (409, 404, 400...) es responsabilidad exclusiva de
 * {@code infrastructure.adapter.in.rest.GlobalExceptionHandler}. El dominio
 * solo sabe "esto no se pudo hacer y por qué", nunca "esto es un 409".
 */
package com.planazo.minijuego.domain.exception;
