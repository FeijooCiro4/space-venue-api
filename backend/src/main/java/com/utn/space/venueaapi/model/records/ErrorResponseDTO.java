package com.utn.space.venueaapi.model.records;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

//El campo de errores es opcional. Se usa en una unica excepcion

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDTO(int status, String error, String mensaje, Map<String, String> errores) {

    public ErrorResponseDTO(int status, String error, String mensaje) {
        this(status, error, mensaje, null);
    }
}
