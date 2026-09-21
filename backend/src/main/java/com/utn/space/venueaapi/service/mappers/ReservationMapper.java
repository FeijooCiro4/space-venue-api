package com.utn.space.venueaapi.service.mappers;

import com.utn.space.venueaapi.model.Consumer;
import com.utn.space.venueaapi.model.Reservation;
import com.utn.space.venueaapi.model.Space;
import com.utn.space.venueaapi.model.ServiceSelected;
import com.utn.space.venueaapi.model.records.ReservationDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface ReservationMapper {

    @Mapping(target = "idConsumer", source = "consumer.idConsumer")
    @Mapping(target = "idSpace", source = "space.idSpace")
    @Mapping(target = "idServicesSelec", source = "services", qualifiedByName = "mapServicesToIds")
    ReservationDTO toDTO(Reservation reservation);

    @Mapping(target = "consumer", ignore = true)
    @Mapping(target = "space", ignore = true)
    @Mapping(target = "services", ignore = true)
    Reservation toEntity(ReservationDTO dto);

    // Convierte las entidades a una lista de IDs para el DTO
    @Named("mapServicesToIds")
    default List<Integer> mapServicesToIds(List<ServiceSelected> services) {
        if (services == null) return null;
        return services.stream()
                .map(ServiceSelected::getId) // Extrae el id de cada fila de servicesselected
                .collect(Collectors.toList());
    }

    default Consumer mapConsumer(Integer id){
        if(id == null) return null;
        Consumer c = new Consumer();
        c.setIdConsumer(id);
        return c;
    }

    default Space mapSpace(Integer id){
        if(id == null) return null;
        Space s = new Space();
        s.setIdSpace(id);
        return s;
    }
}