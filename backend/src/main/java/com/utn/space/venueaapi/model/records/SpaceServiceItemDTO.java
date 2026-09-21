package com.utn.space.venueaapi.model.records;

import com.utn.space.venueaapi.model.flags.Create;
import com.utn.space.venueaapi.model.flags.Update;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema( description = "DTO de Servicio")
public record SpaceServiceItemDTO(
        @NotNull(groups = Update.class)
        @Schema(description = "Identificador único", example = "9")
        Integer id,

        @NotBlank(groups = {Create.class, Update.class})
        @Schema(description = "Descripcion del Servicio", example = "Globos Inflables")
        String description,

        @NotNull(groups = {Create.class, Update.class})
        @Positive(groups = {Create.class, Update.class})
        @Schema(description = "Precio del Servicio", example = "75000.00")
        BigDecimal price,

        @NotNull(groups = {Create.class, Update.class})
        @Schema(description = "Identifica si esta activo")
        Boolean isActive,

        @NotNull(groups = {Create.class, Update.class})
        @Schema(description = "Identificador único del Espacio al que pertenece", example = "9")
        Integer idSpace
) {}
