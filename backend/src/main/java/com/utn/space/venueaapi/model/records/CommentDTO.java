package com.utn.space.venueaapi.model.records;

import com.utn.space.venueaapi.model.flags.Create;
import com.utn.space.venueaapi.model.flags.Update;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

@Schema( description = "DTO de Comentarios")

public record CommentDTO (
        @NotEmpty(groups =  Update.class)
        @Schema(description = "Identificador único", example = "1")
        Integer idComment,

        @NotEmpty(groups = {Create.class, Update.class})
        @Schema(description = "Identificador del usuario que lo creo", example = "22")
        Integer idConsumer,

        @NotEmpty(groups = {Create.class, Update.class})
        @Schema(description = "Username del usuario que lo creo", example = "Pepe")
        String username,

        @NotEmpty(groups = {Create.class, Update.class})
        @Schema(description = "Identificador del espacio en el que se hace el comentario", example = "40")
        Integer idSpace,

        @NotBlank(groups = {Create.class, Update.class})
        @Schema(description = "Detalle del comentario")
        String description,

        @NotNull(groups = {Create.class, Update.class})
        @Min(value = 0, message = "La calificacion minima es 0")
        @Max(value = 5, message = "La calificacion maxima es 5")
        @Schema(description = "Valoracion del comentario", example = "1")
        Byte score,

        LocalDateTime created_at){

}
