package com.utn.space.venueaapi.exceptions;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.utn.space.venueaapi.model.records.ErrorResponseDTO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> accessDenied(AccessDeniedException e) {
        return construirError(HttpStatus.FORBIDDEN, "No tiene permisos para acceder a este recurso");
    }

    @ExceptionHandler(IdNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> idNotFound (IdNotFoundException e){
        return construirError(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ReservationLimitException.class)
    public ResponseEntity<ErrorResponseDTO> reservationLimit(ReservationLimitException e) {
        return construirError(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(SelfReservationException.class)
    public ResponseEntity<ErrorResponseDTO> selfReservation (SelfReservationException e){
        return construirError(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidDateException.class)
    public ResponseEntity<ErrorResponseDTO> invalidDate(InvalidDateException e){
        return construirError(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidDataException.class)
    public ResponseEntity<ErrorResponseDTO> invalidData(InvalidDataException e) {
        return construirError(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidReservationException.class)
    public ResponseEntity<ErrorResponseDTO> invalidReservation(InvalidReservationException e){
        return construirError(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(SpaceUnavailableException.class)
    public ResponseEntity<ErrorResponseDTO> spaceUnavailable(SpaceUnavailableException e){
        return construirError(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ServiceOutOfPlaceException.class)
    public ResponseEntity<ErrorResponseDTO> idNotFound (ServiceOutOfPlaceException e){
        return construirError(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(NameNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> nameNotFound(NameNotFoundException e) {
        return construirError(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)    ///este lo copie de GPT, maneja la validacion de la API
    public ResponseEntity<ErrorResponseDTO> manejarValidaciones(
            MethodArgumentNotValidException ex) {

        Map<String, String> errores = new HashMap<>();

        ex.getBindingResult().getFieldErrors()
                .forEach(error ->
                        errores.put(error.getField(),
                                error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(new ErrorResponseDTO(400, "Bad Request", "Los datos enviados no son válidos.", errores));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDTO> invalidMethodArguments(HandlerMethodValidationException e) {
        return construirError(HttpStatus.valueOf(e.getStatusCode().value()), "Los datos enviados no son validos");
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponseDTO> RespuestaIO (IOException ex){
        return construirError(HttpStatus.INTERNAL_SERVER_ERROR, "Error de IO");
    }

    @ExceptionHandler(Exception.class)// un generico por si las dudas
    public ResponseEntity<ErrorResponseDTO> RespuestaGenerica (Exception ex){
        log.error("Error no controlado", ex);
        return construirError(HttpStatus.INTERNAL_SERVER_ERROR, "Error inesperado");
    }

    private ResponseEntity<ErrorResponseDTO> construirError(HttpStatus status, String mensaje) {
    return ResponseEntity
            .status(status)
            .body(new ErrorResponseDTO(status.value(), status.getReasonPhrase(), mensaje));
}

}
