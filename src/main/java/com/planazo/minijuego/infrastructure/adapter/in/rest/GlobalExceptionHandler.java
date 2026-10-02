package com.planazo.minijuego.infrastructure.adapter.in.rest;

import com.planazo.minijuego.domain.exception.DomainException;
import com.planazo.minijuego.domain.exception.JugadorNoEnSalaException;
import com.planazo.minijuego.domain.exception.JugadorYaEnSalaException;
import com.planazo.minijuego.domain.exception.PartidaNoEnCursoException;
import com.planazo.minijuego.domain.exception.PartidaYaIniciadaException;
import com.planazo.minijuego.domain.exception.SalaLlenaException;
import com.planazo.minijuego.domain.exception.SalaNoEncontradaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce cada {@link DomainException} a un código HTTP estándar — esta
 * es la ÚNICA clase del servicio que conoce la relación entre una regla
 * de negocio incumplida y un número de status HTTP. El dominio lanza
 * excepciones con significado de negocio; nunca un status code.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SalaNoEncontradaException.class)
    public ResponseEntity<ApiError> manejarSalaNoEncontrada(SalaNoEncontradaException ex) {
        return responder(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(SalaLlenaException.class)
    public ResponseEntity<ApiError> manejarSalaLlena(SalaLlenaException ex) {
        return responder(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(PartidaYaIniciadaException.class)
    public ResponseEntity<ApiError> manejarPartidaYaIniciada(PartidaYaIniciadaException ex) {
        return responder(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(JugadorYaEnSalaException.class)
    public ResponseEntity<ApiError> manejarJugadorYaEnSala(JugadorYaEnSalaException ex) {
        return responder(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(JugadorNoEnSalaException.class)
    public ResponseEntity<ApiError> manejarJugadorNoEnSala(JugadorNoEnSalaException ex) {
        return responder(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(PartidaNoEnCursoException.class)
    public ResponseEntity<ApiError> manejarPartidaNoEnCurso(PartidaNoEnCursoException ex) {
        return responder(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> manejarDomainException(DomainException ex) {
        return responder(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> manejarAccesoDenegado(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.de("ACCESO_DENEGADO", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> manejarIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError.de("SOLICITUD_INVALIDA", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> manejarError(Exception ex) {
        log.error("Error inesperado atendiendo una solicitud REST del Minijuego.", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.de("ERROR_INESPERADO", "Ocurrió un error inesperado. Intenta de nuevo."));
    }

    private ResponseEntity<ApiError> responder(HttpStatus status, DomainException ex) {
        return ResponseEntity.status(status).body(ApiError.de(ex.codigo(), ex.getMessage()));
    }
}
