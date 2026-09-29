package com.utn.space.venueaapi.model.records;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Contrato de lectura del catálogo: ninguna propiedad contiene entidades JPA. */
public record SpaceResponseDTO(
        Integer idSpace, PublicOwner consumerOwner, PublicLocation location,
        PublicCancellationPolicy cancellationPolicies, String nameSpace, String description,
        BigDecimal basePrice, LocalDate publicationDate, Integer bufferTime,
        Boolean isActive, List<PublicService> services
) {
    public record PublicOwner(Integer idConsumer, String firstname, String lastname) {}
    public record PublicLocation(Integer idLocation, BigDecimal longitude, BigDecimal latitude) {}
    public record PublicCancellationPolicy(Integer idCancellationPolicies, String type,
                                           Integer daysAnticipation, BigDecimal refundPercentage) {}
    public record PublicService(Integer id, String description, BigDecimal price, Boolean isActive) {}
}
