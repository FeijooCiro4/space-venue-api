package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.IdNotFoundException;
import com.utn.space.venueaapi.exceptions.InvalidReservationException;
import com.utn.space.venueaapi.model.Reservation;
import com.utn.space.venueaapi.model.ServiceSelected;
import com.utn.space.venueaapi.model.records.SelectServiceDTO;
import com.utn.space.venueaapi.model.records.ServiceSelectedDTO;
import com.utn.space.venueaapi.repository.ServiceSelectedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@RequiredArgsConstructor
@Service
public class ServiceSelectedService {
    private final ServiceSelectedRepository serviceSelectedRepository;
    private final ReservationService reservationService;
    private final SpaceServiceItemService spaceServiceItemService;

    public List<ServiceSelectedDTO> getServicesSelectedOfReservation(Integer idReservation) {
        return serviceSelectedRepository.findServiceSelectedByIdReservation(idReservation);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void insertListOfServicesSelectedInAReservation(Integer idReservation, List<SelectServiceDTO> selections) {
        Reservation reservation = reservationService.findByIdForUpdate(idReservation);
        reservationService.requireEditable(reservation);
        if (selections == null || selections.isEmpty()) {
            throw new InvalidReservationException("Debe seleccionar al menos un servicio del catálogo.");
        }
        BigDecimal base = bookedBasePrice(reservation);
        List<ServiceSelected> additions = new ArrayList<>();
        var ids = new HashSet<Integer>();
        for (SelectServiceDTO selection : selections) {
            if (selection == null || selection.idService() == null || selection.idService() <= 0
                    || !ids.add(selection.idService())) {
                throw new InvalidReservationException("Los IDs de servicios deben ser positivos y no repetidos.");
            }
            var item = spaceServiceItemService.findById(selection.idService());
            if (!item.getSpace().getIdSpace().equals(reservation.getSpace().getIdSpace())
                    || !Boolean.TRUE.equals(item.getIsActive())) {
                throw new InvalidReservationException("El servicio debe estar activo y pertenecer al espacio reservado.");
            }
            if (item.getPrice() == null || item.getPrice().signum() <= 0) {
                throw new InvalidReservationException("El servicio del catálogo no tiene un precio válido.");
            }
            additions.add(new ServiceSelected(item, reservation));
        }
        // Validar la lista completa antes de modificar la colección administrada.
        reservation.getServices().addAll(additions);
        recalculateTotal(reservation, base);
        // La relación cascade ALL guarda las selecciones junto al total en esta transacción.
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteServiceSelectedForAReservation(Integer id) {
        var selected = serviceSelectedRepository.findById(id)
                .orElseThrow(() -> new IdNotFoundException("Servicio seleccionado", id));
        Reservation reservation = reservationService.findByIdForUpdate(selected.getReservation().getId());
        reservationService.requireEditable(reservation);
        BigDecimal base = bookedBasePrice(reservation);
        if (!reservation.getServices().removeIf(service -> service.getId().equals(id))) {
            throw new IdNotFoundException("Servicio seleccionado", id);
        }
        recalculateTotal(reservation, base);
        // orphanRemoval borra el registro sin dejarlo en la colección del padre.
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteSelectedServiceByReserveId(Integer idReservation) {
        Reservation reservation = reservationService.findByIdForUpdate(idReservation);
        reservationService.requireEditable(reservation);
        BigDecimal base = bookedBasePrice(reservation);
        reservation.getServices().clear();
        recalculateTotal(reservation, base);
    }

    private BigDecimal selectedTotal(Reservation reservation) {
        return reservation.getServices().stream().map(ServiceSelected::getPriceAtReservation)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal bookedBasePrice(Reservation reservation) {
        // Conservar el alquiler ya cotizado aunque el dueño cambie después la tarifa del espacio.
        BigDecimal base = reservation.getFinalPrice().subtract(selectedTotal(reservation));
        if (base.signum() < 0) {
            throw new InvalidReservationException("El total de la reserva es inconsistente con sus servicios.");
        }
        return base;
    }

    private void recalculateTotal(Reservation reservation, BigDecimal base) {
        reservation.setFinalPrice(base.add(selectedTotal(reservation)));
    }
}
