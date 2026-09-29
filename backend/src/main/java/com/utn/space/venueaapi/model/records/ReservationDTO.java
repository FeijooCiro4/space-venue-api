package com.utn.space.venueaapi.model.records;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.utn.space.venueaapi.model.ReservationStatus;
import com.utn.space.venueaapi.model.flags.Create;
import com.utn.space.venueaapi.model.flags.Update;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

@Schema( description = "DTO de Reservas")
public record ReservationDTO (
    @NotNull(groups = Update.class)
    @Schema(description = "Identificador único", example = "1")
    Integer id,

    @NotBlank(groups = {Create.class, Update.class})
    @Schema(description = "Nombre de la reserva", example = "Cumple de Sofia")
    String title,

    @NotBlank(groups = {Create.class, Update.class})
    @Schema(description = "Descripción del Evento", example = "El cumpleaños de 15 de Sofia")
    String description,


    @NotNull(groups = {Create.class, Update.class})
    @Schema(description = "Fecha de Inicio del Evento", example = "2026-09-15T21:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime fromDate,

    @NotNull(groups = {Create.class, Update.class})
    @Schema(description = "Fecha de Fin del Evento", example = "2026-09-16T05:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime untilDate,

    @Schema(description = "Precio final de la reserva")
    Double finalPrice,

    @Schema(description = "Estado de la Reserva", example = "CONFIRMED")
    ReservationStatus status,

    @Schema(description = "Fecha en la que se hizo la reserva", example = "2026-05-20T011:25:31")
    LocalDateTime createdAt,

    @Schema(description = "Flag para saber si se hizo un softdelete en la reserva", example = "2026-05-20T011:25:31")
    Boolean isActive,

    // Para la creación (Create) el backend toma el consumidor del JWT, por eso
    // solo es obligatorio en el grupo Update (cuando se modifica una reserva existente).
    @NotNull(groups = {Update.class})
    @Schema(description = "ID del usuario que creo la reserva")
    Integer idConsumer,

    @NotNull(groups = {Create.class, Update.class})
    @Schema(description = "ID del espacio a reservar", example = "2026-05-20T011:25:31")
    Integer idSpace,


    @Schema(description = "Lista de las IDs de los servicios agregados")
    List<Integer> idServicesSelec
){
}
