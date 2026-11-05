package com.utn.space.venueaapi.model.records;


public record ConsumerFilterDTO(
        String firstname,
        String lastname,
        String email,
        String phone,
        // Solo lo usa la búsqueda de /usuarios/byfields (parcial, sin distinguir mayúsculas).
        // PUT /usuario lo ignora: el username no se puede modificar
        String username
) {}
