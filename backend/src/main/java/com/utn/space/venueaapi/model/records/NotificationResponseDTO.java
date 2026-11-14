package com.utn.space.venueaapi.model.records;

import io.swagger.v3.oas.annotations.media.Schema;

// Solo se utiliza para la documentación de la API, no se utiliza en el código de la aplicación

@Schema(description = "Representación de una notificación")
public record NotificationResponseDTO(
        @Schema(description = "Identificador de la notificación", example = "12")
        Integer idNotification,

        @Schema(description = "Alias de idNotification", example = "12")
        Integer id,

        @Schema(description = "Texto de la notificación", example = "Tu reserva fue confirmada.")
        String message,

        @Schema(description = "Indica si la notificación fue vista", example = "false")
        Boolean isSeen,

        @Schema(description = "Alias de isSeen", example = "false")
        Boolean seen,

        @Schema(description = "Fecha de creación en milisegundos Unix UTC", example = "1794610800000")
        Long createdAt,

        @Schema(nullable = true, description = "Campo reservado; actualmente se devuelve null")
        String type,

        @Schema(nullable = true, description = "Campo reservado; actualmente se devuelve null")
        String detail
) {}