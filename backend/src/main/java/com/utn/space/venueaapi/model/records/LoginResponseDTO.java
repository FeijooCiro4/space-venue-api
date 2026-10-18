package com.utn.space.venueaapi.model.records;

public record LoginResponseDTO(String token, String tipo, String rol, Integer consumerId) {}
