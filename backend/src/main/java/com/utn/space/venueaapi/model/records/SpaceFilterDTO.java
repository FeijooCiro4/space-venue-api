package com.utn.space.venueaapi.model.records;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema( description = "Esquema para filtrar Espacios")

public record SpaceFilterDTO(
        Integer idConsumerOwner,
        Integer idLocation,
        String nameSpace,
        Double minPrice,
        Double maxPrice,
        BigDecimal lat, //Latitud del usuario
        BigDecimal lng, //Longitud del usuario
        BigDecimal radious)// Radio de busqueda
{}
