package com.botica.backend.config;

import com.botica.backend.dto.ErrorResponse;
import com.botica.backend.exception.NegocioException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

/**
 * Un solo @RestControllerAdvice para todo el backend (Regla 7). Nunca un
 * 500 con stacktrace al cliente: todo excepción no controlada se atrapa
 * al final y se loguea completa del lado del servidor, pero al cliente
 * solo le llega el envoltorio uniforme.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ErrorResponse> manejarNegocio(NegocioException ex, HttpServletRequest request) {
        return construir(ex.getStatus(), ex.getCodigo(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("Formato inválido");
        return construir(HttpStatus.BAD_REQUEST, "FORMATO_INVALIDO", mensaje, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarJsonInvalido(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "FORMATO_INVALIDO", "El cuerpo de la petición no es JSON válido", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarInesperado(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Ocurrió un error inesperado", request);
    }

    private ResponseEntity<ErrorResponse> construir(HttpStatus status, String codigo, String mensaje, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(OffsetDateTime.now(), status.value(), codigo, mensaje, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
