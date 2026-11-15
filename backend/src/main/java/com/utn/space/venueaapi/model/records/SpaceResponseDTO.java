package com.utn.space.venueaapi.model.records;

import com.utn.space.venueaapi.model.ESpaceDeactivationReason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;


public record SpaceResponseDTO(
        Integer idSpace, PublicOwner consumerOwner, PublicLocation location,
        PublicCancellationPolicy cancellationPolicies, String nameSpace, String description,
        BigDecimal basePrice, LocalDate publicationDate, Integer bufferTime,
        Double rating, Integer commentCount,
        Boolean isActive, Boolean deletedByOwner, ESpaceDeactivationReason deactivationReason,
        // false si el dueño está dado de baja o ya no es oferente: el espacio queda oculto aunque isActive sea true
        Boolean ownerAvailable,
        List<PublicService> services
) {
    public record PublicOwner(Integer idConsumer, String firstname, String lastname) {}
    public record PublicLocation(Integer idLocation, BigDecimal longitude, BigDecimal latitude) {}
    public record PublicCancellationPolicy(Integer idCancellationPolicies, String type,
                                           Integer daysAnticipation, BigDecimal refundPercentage) {}
    public record PublicService(Integer id, String description, BigDecimal price, Boolean isActive) {}
}
