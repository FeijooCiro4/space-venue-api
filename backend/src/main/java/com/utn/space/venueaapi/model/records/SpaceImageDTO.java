package com.utn.space.venueaapi.model.records;

import com.utn.space.venueaapi.model.flags.Create;
import com.utn.space.venueaapi.model.flags.Update;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema( description = "DTO de Imagenes")
public record SpaceImageDTO (
        @NotNull(groups = Update.class)
        @Schema(description = "Identificador único", example = "1")
        Integer idImage,

        @NotNull(groups = {Create.class, Update.class})
        @Schema(description = "Identificador del Espacio al que pertenece", example = "74")
        Integer idSpace,

        @NotBlank(groups = {Create.class, Update.class})
        @Schema(description = "Nombre del achivo")
        String fileName,

        @NotBlank(groups = {Create.class, Update.class})
        @Schema(description = "Dirección del archivo")
        String urlImage,

        @NotNull(groups =  Update.class)
        @Schema(description = "Fecha en la que se subió la imagen",  example = "2026-06-13T011:25:31")
        LocalDateTime dateSend){
}
