package com.utn.space.venueaapi.model.records;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "URL prefirmada para subir la imagen con un PUT, y la key con la que queda guardada")
public record ImageUploadUrlResponseDTO(String uploadUrl, String key) {}
