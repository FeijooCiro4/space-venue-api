package com.utn.space.venueaapi.model.records;

import com.utn.space.venueaapi.model.flags.Create;
import com.utn.space.venueaapi.model.flags.CreateAdmin;
import com.utn.space.venueaapi.model.flags.CreateOwned;
import com.utn.space.venueaapi.model.flags.Update;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "DTO de Espacio")
public record SpaceDTO(
        @NotNull(groups = Update.class)
        @Schema(description = "Identificador único", example = "1")
        Integer idSpace,

        @NotNull(groups = {CreateAdmin.class, Update.class})
        @Positive(groups = {CreateAdmin.class, Update.class})
        @Schema(description = "Identificador único del dueño", example = "22")
        Integer idConsumerOwner,

        @NotNull(groups = {Create.class, Update.class})
        @Valid
        @Schema(description = "Una locación")
        LocationDTO location,

        @NotNull(groups = {Create.class, Update.class})
        @NotBlank(groups = {Create.class, Update.class})
        @Schema(description = "Una Politica de cancelación")
        String cancellationPolicies,


        @NotBlank(groups = {Create.class, Update.class})
        @Schema(description = "Nombre del Espacio", example = "Salon de Fiestas: Ejemplo")
        String nameSpace,

        @NotBlank(groups = {Create.class, Update.class})
        @Schema(description = "Descripcion del Espacio")
        String description,

        @NotNull(groups = {Create.class, Update.class})
        @Positive(groups = {Create.class, Update.class})
        @Schema(description = "Precio del alquiler", example = "250000")
        BigDecimal basePrice,

        LocalDate publicationDate,

        @NotNull(groups = {Create.class, Update.class})
        @Positive(groups = {Create.class, Update.class})
        @Schema(description = "Tiempo entre alquileres")
        Integer bufferTime,

        @Schema(description = "Un booleano para saber si esta activo")
        Boolean active,

        @Schema(description = "Lista de servicios")
        List<ServiceItemDTO> services
) {}
