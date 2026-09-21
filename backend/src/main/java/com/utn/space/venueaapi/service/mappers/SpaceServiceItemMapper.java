package com.utn.space.venueaapi.service.mappers;

import com.utn.space.venueaapi.model.Space;
import com.utn.space.venueaapi.model.SpaceServiceItem;
import com.utn.space.venueaapi.model.records.SpaceServiceItemDTO;

public class SpaceServiceItemMapper {
    public static SpaceServiceItemDTO toDto(SpaceServiceItem spaceServiceItem){
        return new SpaceServiceItemDTO(
                spaceServiceItem.getId(),
                spaceServiceItem.getDescription(),
                spaceServiceItem.getPrice(),
                spaceServiceItem.getIsActive(),
                spaceServiceItem.getSpace().getIdSpace()
        );
    }

    public static SpaceServiceItem toEntity(SpaceServiceItemDTO serviceItemDTO, Space space){
        return new SpaceServiceItem(
                serviceItemDTO.id(),
                serviceItemDTO.description(),
                serviceItemDTO.price(),
                serviceItemDTO.isActive(),
                space
        );
    }

    public static SpaceServiceItem toEntity(SpaceServiceItemDTO serviceItemDTO, Integer id, Space space){
        return new SpaceServiceItem(
                id,
                serviceItemDTO.description(),
                serviceItemDTO.price(),
                serviceItemDTO.isActive(),
                space
        );
    }
}
