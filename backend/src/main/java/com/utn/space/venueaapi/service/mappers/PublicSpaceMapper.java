package com.utn.space.venueaapi.service.mappers;

import com.utn.space.venueaapi.model.Space;
import com.utn.space.venueaapi.model.SpaceImage;
import com.utn.space.venueaapi.model.records.SpaceResponseDTO;
import com.utn.space.venueaapi.model.records.SpaceImageResponseDTO;
import java.util.List;

public final class PublicSpaceMapper {
    private PublicSpaceMapper() {}

    public static SpaceResponseDTO toDto(Space space) {
        if (space == null) return null;
        var owner = space.getConsumerOwner();
        var location = space.getLocation();
        var policy = space.getCancellationPolicies();
        return new SpaceResponseDTO(space.getIdSpace(),
                owner == null ? null : new SpaceResponseDTO.PublicOwner(owner.getIdConsumer(), owner.getFirstname(), owner.getLastname()),
                location == null ? null : new SpaceResponseDTO.PublicLocation(location.getIdLocation(), location.getLongitude(), location.getLatitude()),
                policy == null ? null : new SpaceResponseDTO.PublicCancellationPolicy(policy.getIdCancellationPolicies(),
                        policy.getType() == null ? null : policy.getType().name(), policy.getDaysAnticipation(), policy.getRefundPercentage()),
                space.getNameSpace(), space.getDescription(), space.getBasePrice(), space.getPublicationDate(),
                space.getBufferTime(), space.getIsActive(),
                space.getServices() == null ? List.of() : space.getServices().stream()
                        .map(item -> new SpaceResponseDTO.PublicService(item.getId(), item.getDescription(), item.getPrice(), item.getIsActive()))
                        .toList());
    }

    public static SpaceImageResponseDTO toDto(SpaceImage image) {
        return new SpaceImageResponseDTO(image.getIdSpaceImages(), toDto(image.getSpace()),
                image.getFileName(), image.getUrlImage(), image.getDateSend());
    }
}
