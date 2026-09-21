package com.utn.space.venueaapi.service.mappers;

import com.utn.space.venueaapi.model.Reservation;
import com.utn.space.venueaapi.model.ServiceSelected;
import com.utn.space.venueaapi.model.records.ServiceSelectedDTO;

public class ServiceSelectedMapper {

    public static ServiceSelectedDTO toDto(ServiceSelected serviceSelected){
        return new ServiceSelectedDTO(
                serviceSelected.getId(),
                serviceSelected.getPriceAtReservation(),
                serviceSelected.getDescriptionFrozen(),
                serviceSelected.getReservation().getId()
        );
    }

    public static ServiceSelected toEntity(ServiceSelectedDTO serviceSelectedDTO, Reservation reservation){
        return new ServiceSelected(
                null,
                serviceSelectedDTO.priceAtReservation(),
                serviceSelectedDTO.descriptionFrozen(),
                reservation
        );
    }
}
