package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.IdNotFoundException;
import com.utn.space.venueaapi.model.records.ServiceSelectedDTO;
import com.utn.space.venueaapi.repository.ReservationRepository;
import com.utn.space.venueaapi.repository.ServiceSelectedRepository;
import com.utn.space.venueaapi.service.mappers.ServiceSelectedMapper;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@AllArgsConstructor
@Service
public class ServiceSelectedService {
    @Autowired
    private final ServiceSelectedRepository serviceSelectedRepository;
    @Autowired
    private final ReservationRepository reservationRepository;
    @Autowired
    private final SpaceServiceItemService spaceServiceItemService;

    public List<ServiceSelectedDTO> getServicesSelectedOfReservation(Integer idReservation){
        return serviceSelectedRepository.findServiceSelectedByIdReservation(idReservation);
    }

    @Transactional
    public void insertListOfServicesSelectedInAReservation(Integer idReservation, List<ServiceSelectedDTO> servicesSelectedDTO){
        //Este metodo recibe el id de una reserva y una lista de servicios seleccionados (por servicesSelectedDTO) y los mete en la tabla ServicesSelected
        for(ServiceSelectedDTO serviceSelectedDTO : servicesSelectedDTO){
            serviceSelectedRepository.save(ServiceSelectedMapper.toEntity(serviceSelectedDTO,
                    reservationRepository.findById(idReservation)
                            .orElseThrow(() -> new IdNotFoundException(
                                    "No se ha encontrado la reserva para insertar lista de servicios seleccionados: ", idReservation))));
        }
    }

    @Transactional
    public void deleteServiceSelectedForAReservation(Integer id){
        if(!serviceSelectedRepository.existsById(id)) throw new IdNotFoundException("No se encontró el servicio seleccionado para eliminarlo: ", id);

        serviceSelectedRepository.deleteById(id);
    }

    //Borra todos los Selected Services por id_reserva
    @Transactional
    public void deleteSelectedServiceByReserveId (Integer id_reserva){
        serviceSelectedRepository.deleteSelectedServiceByReserveId(id_reserva);
    }
}
