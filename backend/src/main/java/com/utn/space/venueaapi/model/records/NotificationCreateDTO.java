package com.utn.space.venueaapi.model.records;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Datos para crear una notificación")
public record NotificationCreateDTO(
        @NotNull
        @Schema(description = "Identificador del usuario destinatario", example = "15")
        Integer idConsumer,

        @NotBlank
        @Schema(description = "Mensaje de la notificación", example = "Tu reserva fue confirmada.")
        String message
) {
}
