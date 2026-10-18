//Esta clase se encarga de responder a los errores que tira la autenticacion del JWT para que tambien se devuelvan como JSON

package com.utn.space.venueaapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.utn.space.venueaapi.model.records.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
//Formatea las respuestas de error de la misma forma que el Global exception handler
@Component
public class SecurityErrorResponder {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    

    public void escribir(HttpServletResponse response, HttpStatus status, String mensaje) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ErrorResponseDTO(status.value(), status.getReasonPhrase(), mensaje));
    }
}