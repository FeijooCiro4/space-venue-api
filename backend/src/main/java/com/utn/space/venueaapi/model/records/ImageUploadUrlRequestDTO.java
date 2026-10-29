package com.utn.space.venueaapi.model.records;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Pedido de una URL para subir una imagen directamente a S3")
public record ImageUploadUrlRequestDTO(
        @NotNull
        @Schema(description = "Identificador del Espacio al que pertenecerá la imagen", example = "74")
        Integer idSpace,

        @NotBlank
        @Schema(description = "Tipo del archivo", example = "image/jpeg")
        String contentType,

        @NotNull
        @Positive
        @Schema(description = "Tamaño del archivo en bytes", example = "245760")
        Long size) {
}
