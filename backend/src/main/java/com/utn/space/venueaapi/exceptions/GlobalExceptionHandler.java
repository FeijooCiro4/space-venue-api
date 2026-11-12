package com.utn.space.venueaapi.exceptions;


import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.utn.space.venueaapi.model.records.ErrorResponseDTO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

//Todos los errores se devuelven como ErrorResponseDTO con un "mensaje" pensado para mostrarse directamente al usuario
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String MENSAJE_SIN_PERMISOS = "No tiene permisos para realizar esta acción.";
    private static final String MENSAJE_ERROR_INTERNO = "Ocurrió un problema en el servidor. Intente de nuevo más tarde.";

    // ---------- Excepciones propias: su mensaje ya está pensado para el usuario ----------

    @ExceptionHandler({IdNotFoundException.class, NameNotFoundException.class, ServiceOutOfPlaceException.class})
    public ResponseEntity<ErrorResponseDTO> noEncontrado(RuntimeException e) {
        return construirError(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({InvalidDataException.class, InvalidDateException.class, InvalidReservationException.class,
            ReservationLimitException.class, SelfReservationException.class, SpaceUnavailableException.class})
    public ResponseEntity<ErrorResponseDTO> datosInvalidos(RuntimeException e) {
        return construirError(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // ---------- Permisos ----------

    //Las que lanzamos nosotros traen un motivo; las de @PreAuthorize (AuthorizationDeniedException) solo dicen "Access Denied"
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> accesoDenegado(AccessDeniedException e) {
        String mensaje = e instanceof AuthorizationDeniedException || e.getMessage() == null
                ? MENSAJE_SIN_PERMISOS
                : e.getMessage();
        return construirError(HttpStatus.FORBIDDEN, mensaje);
    }

    // ---------- Validaciones ----------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> validacionDelCuerpo(MethodArgumentNotValidException e) {
        Map<String, String> errores = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errores.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(new ErrorResponseDTO(
                400, "Bad Request", "Los datos enviados no son válidos.", errores));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDTO> validacionDeParametros(HandlerMethodValidationException e) {
        String detalle = e.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .collect(Collectors.joining(". "));
        return construirError(HttpStatus.BAD_REQUEST,
                detalle.isEmpty() ? "Los datos enviados no son válidos." : detalle);
    }

    // ---------- Errores de la solicitud que antes caían en el genérico y devolvían 500 ----------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> cuerpoIlegible(HttpMessageNotReadableException e) {
        return construirError(HttpStatus.BAD_REQUEST, "Los datos enviados tienen un formato inválido.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> tipoDeParametroInvalido(MethodArgumentTypeMismatchException e) {
        return construirError(HttpStatus.BAD_REQUEST, "El valor de '" + e.getName() + "' no es válido.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDTO> parametroFaltante(MissingServletRequestParameterException e) {
        return construirError(HttpStatus.BAD_REQUEST, "Falta el dato obligatorio '" + e.getParameterName() + "'.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDTO> archivoDemasiadoGrande(MaxUploadSizeExceededException e) {
        return construirError(HttpStatus.CONTENT_TOO_LARGE, "El archivo supera el tamaño máximo permitido.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> conflictoDeDatos(DataIntegrityViolationException e) {
        log.warn("Violación de integridad de datos", e);
        return construirError(HttpStatus.CONFLICT, "Los datos entran en conflicto con un registro existente.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDTO> rutaInexistente(NoResourceFoundException e) {
        return construirError(HttpStatus.NOT_FOUND, "El recurso solicitado no existe.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> metodoNoSoportado(HttpRequestMethodNotSupportedException e) {
        return construirError(HttpStatus.METHOD_NOT_ALLOWED, "La operación solicitada no está permitida.");
    }

    // ---------- Cualquier otra cosa es un error nuestro: se loguea y no se exponen detalles ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> errorInterno(Exception e) {
        log.error("Error no controlado", e);
        return construirError(HttpStatus.INTERNAL_SERVER_ERROR, MENSAJE_ERROR_INTERNO);
    }

    private ResponseEntity<ErrorResponseDTO> construirError(HttpStatus status, String mensaje) {
        return ResponseEntity
                .status(status)
                .body(new ErrorResponseDTO(status.value(), status.getReasonPhrase(), mensaje));
    }

}
