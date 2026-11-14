package com.utn.space.venueaapi.model.records;

import io.swagger.v3.oas.annotations.media.Schema;

//Este record fue creado únicamente para poder documentar correctamente el endpoint
// de login en Swagger, ya que el DTO original (LoginRequestDTO) no tiene anotaciones de Swagger

@Schema(description = "Credenciales para iniciar sesión")
public record LoginRequestDTO(
        @Schema(description = "Nombre de usuario", example = "Pepe")
        String username,

        @Schema(description = "Contraseña sin codificar", example = "mi-contraseña")
        String password
) {}