package com.utn.space.venueaapi.model.records;

import java.math.BigDecimal;

public record LocationBoundingBox(
        BigDecimal minLatitude,
        BigDecimal maxLatitude,
        BigDecimal minLongitude,
        BigDecimal maxLongitude,
        boolean crossesAntimeridian
) {}
