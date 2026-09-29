package com.utn.space.venueaapi.model.records;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Entrada de selección: los valores contratados se obtienen exclusivamente del catálogo. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SelectServiceDTO(
        @NotNull @Positive
        @Schema(description = "ID del servicio del catálogo del espacio", example = "1")
        Integer idService
) {}
