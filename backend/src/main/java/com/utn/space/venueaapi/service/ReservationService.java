package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.*;
import com.utn.space.venueaapi.model.*;
import com.utn.space.venueaapi.model.records.ReservationDTO;
import com.utn.space.venueaapi.repository.SpaceServiceItemRepository;
import com.utn.space.venueaapi.service.mappers.ReservationMapper;
import com.utn.space.venueaapi.repository.ReservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ReservationService {
    @Autowired
    private ReservationMapper reservationMapper;

    @Autowired
    private ServiceSelectedService serviceSelectedService;

    @Autowired
    private ConsumerService consumerService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SpaceService spaceService;

    @Autowired
    private NotificationService notificationService;


    private final SpaceServiceItemRepository spaceServiceItemRepository;

    public ReservationService(SpaceServiceItemRepository spaceServiceItemRepository) {

        this.spaceServiceItemRepository = spaceServiceItemRepository;
    }

    ///--------------------------------------------Metodos------------------------------------------------------------------------------

    public List<Reservation> findAll (){
        return reservationRepository.findAll();
    }

    public Reservation findById (Integer id){
        return reservationRepository.findById(id).orElseThrow(()-> new IdNotFoundException("Reservacion",id));
    }

    public List<Reservation> findByIdConsumer(Integer id){
        return reservationRepository.findAllByConsumer_IdConsumer(id);
    }

    public List<Reservation> findAllForLoggedConsumer() {
        Integer loggedCustomerId = consumerService.getLoggedConsumerId();
        return reservationRepository.findAllByConsumer_IdConsumer(loggedCustomerId);
    }

    private boolean isSpaceAvailableBetweenDates(LocalDateTime from, LocalDateTime until, Integer spaceId){
        List<Reservation> reservationsForSpace = reservationRepository.findAllBySpace_IdSpace(spaceId);
        
        // Filtro para mantener SOLO las reservas activas y confirmadas (no canceladas/rechazadas)
        reservationsForSpace = reservationsForSpace.stream().
                filter(reservation -> !reservation.getStatus().equals(ReservationStatus.CANCELLED)
                        && !reservation.getStatus().equals(ReservationStatus.REJECTED)
                        && reservation.getIsActive()).toList();

        Integer bufferTime = spaceService.findById(spaceId).getBufferTime();

        // Busco si alguna de las reservas que quedan se solapa con la reserva actual. Se le suma el buffer time a el untilDate de la reserva
        return !reservationsForSpace.stream().anyMatch(reservation -> !reservation.getFromDate().isAfter(until) && !reservation.getUntilDate().plusMinutes(bufferTime).isBefore(from));
    }

    @Transactional
    public Reservation saveReservation(Reservation aux) {
        return reservationRepository.save(aux);
    }

    public Reservation create(ReservationDTO dto) {
        if (dto.untilDate().isBefore(dto.fromDate())) {
            throw new InvalidDateException("La Fecha Final no puede ser antes que la Fecha de Inicio");
        }
        if (dto.fromDate().isBefore(LocalDateTime.now())) {
            throw new InvalidDateException("La Fecha de Inicio no puede ser en el pasado.");
        }

        Integer idLogueado = consumerService.getLoggedConsumerId();

        if (!isSpaceAvailableBetweenDates(dto.fromDate(),dto.untilDate(),dto.idSpace())){
            throw new InvalidReservationException("La reserva no esta disponible en la fechas seleccionadas");
        }

        // Determinar el consumidor asociado a la reserva.
        // Un `idConsumer` arbitrario en el DTO no debe permitir
        // reservar a nombre de otra persona. Usamos el usuario logueado salvo que
        // el DTO explícitamente indique el mismo id.
        Consumer client;
        if (dto.idConsumer() == null || !idLogueado.equals(dto.idConsumer())) {
            client = consumerService.findById(idLogueado);
        } else {
            client = consumerService.findById(dto.idConsumer());
        }

        Space space = spaceService.findById(dto.idSpace());

        if (space.getConsumerOwner().getIdConsumer().equals(idLogueado)) {
            throw new SelfReservationException("Señor Administrador/Anfitrión: No puede reservar su propio espacio comercial.");
        }

        // Validar límite de 5 reservas completadas/confirmadas por espacio
        long reservasCompletadas = reservationRepository.countCompletedReservationsByConsumerAndSpace(idLogueado, dto.idSpace());
        if (reservasCompletadas >= 5) {
            throw new ReservationLimitException("Has alcanzado el límite máximo de 5 reservas en este espacio. No puedes reservar nuevamente este espacio.");
        }

        Reservation aux = reservationMapper.toEntity(dto);
        aux.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        aux.setStatus(ReservationStatus.TENTATIVE);
        aux.setIsActive(true);

        aux.setConsumer(client);

        aux.setSpace(space);

        List<ServiceSelected> serviciosSeleccionados = new ArrayList<>();
        BigDecimal totalServicios = BigDecimal.ZERO;

        // Defensa contra nulos si el usuario no seleccionó ningún opcional
        if (dto.idServicesSelec() != null) {
            for (Integer idService : dto.idServicesSelec()) {
                SpaceServiceItem servicioCatalogo = spaceServiceItemRepository.findById(idService)
                        .orElseThrow(() -> new IdNotFoundException("Servicio Catálogo", idService));

                if (!servicioCatalogo.getSpace().getIdSpace().equals(space.getIdSpace())) {
                    throw new ServiceOutOfPlaceException("El servicio con ID " + idService + " no corresponde al espacio seleccionado.");
                }

                ServiceSelected selected = new ServiceSelected(servicioCatalogo, aux);
                totalServicios = totalServicios.add(servicioCatalogo.getPrice());
                serviciosSeleccionados.add(selected);
            }
        }

        aux.setServices(serviciosSeleccionados);

        // Calcular precio por horas
        long minutosDiferencia = java.time.temporal.ChronoUnit.MINUTES.between(dto.fromDate(), dto.untilDate());
        double horasDiferencia = minutosDiferencia / 60.0;
        BigDecimal precioBase = space.getBasePrice().multiply(BigDecimal.valueOf(horasDiferencia));

        aux.setFinalPrice(precioBase.add(totalServicios));

        Reservation reservaGuardada = saveReservation(aux);

        // Crear notificación al dueño del espacio
        String mensajeNotificacion = "Nueva reserva de " + client.getFirstname() + " " + client.getLastname() +
                " para " + space.getNameSpace() + ". Confirmá o rechazá la reserva.";
        notificationService.createNotification(space.getConsumerOwner(), mensajeNotificacion);

        return reservaGuardada;
    }


    @Transactional
    public Reservation modify (ReservationDTO dto){
        if (dto.untilDate().isBefore(dto.fromDate())) {
            throw new InvalidDateException("La Fecha Final no puede ser antes que la Fecha de Inicio");
        }
        if (dto.fromDate().isBefore(LocalDateTime.now())) {
            throw new InvalidDateException("La Fecha Final no puede ser antes que la Fecha de Inicio");
        }
        if(!reservationRepository.existsById(dto.id())){
            throw new IdNotFoundException ("Reservation", dto.id());
        }
        Reservation nuevaReserva = reservationMapper.toEntity(dto);

        nuevaReserva.setConsumer(consumerService.findById(dto.idConsumer()));

        nuevaReserva.setSpace(spaceService.findById(dto.idSpace()));


        //limpiar servicios seleccionados anteriores
        serviceSelectedService.deleteSelectedServiceByReserveId(dto.id());

        List<ServiceSelected> list= new ArrayList<>();
        list= nuevaReserva.getSpace().getServices().stream()
                .filter(item->dto.idServicesSelec().contains(item.getId()))//filtro todos los serviceItem Seleccionados para la reserva
                .map(item-> new ServiceSelected(item, nuevaReserva))  //transformo los item en serviceSelected
                .toList();

         nuevaReserva.setServices(list);

         // Calcular precio por horas
         long minutosDiferencia = java.time.temporal.ChronoUnit.MINUTES.between(dto.fromDate(), dto.untilDate());
         double horasDiferencia = minutosDiferencia / 60.0;
         BigDecimal precioBase = nuevaReserva.getSpace().getBasePrice().multiply(BigDecimal.valueOf(horasDiferencia));

         nuevaReserva.setFinalPrice(
                 precioBase.add( //el + no funciona con bigDecimal
                         nuevaReserva.getServices()
                                 .stream()
                                 .map(ServiceSelected::getPriceAtReservation)
                                 .reduce(BigDecimal.ZERO, BigDecimal::add)
                 )
         );
        return reservationRepository.save(nuevaReserva);
    }

    public Reservation confirmReservation(Integer id){
        Reservation aux= reservationRepository.findById(id).orElseThrow(()->new IdNotFoundException ("Reservation", id));
        aux.setStatus(ReservationStatus.CONFIRMED);
        Reservation confirmed = reservationRepository.save(aux);

        // Notificar al usuario que su reserva fue confirmada
        String mensaje = "Tu reserva en " + aux.getSpace().getNameSpace() + " ha sido confirmada. ¡Ya puedes pagar!";
        notificationService.createNotification(aux.getConsumer(), mensaje);

        return confirmed;
    }

    public Reservation rejectReservation(Integer id){
        Reservation aux= reservationRepository.findById(id).orElseThrow(()->new IdNotFoundException ("Reservation", id));
        aux.setStatus(ReservationStatus.REJECTED);
        Reservation rejected = reservationRepository.save(aux);

        // Notificar al usuario que su reserva fue rechazada
        String mensaje = "Tu reserva en " + aux.getSpace().getNameSpace() + " ha sido rechazada por el propietario.";
        notificationService.createNotification(aux.getConsumer(), mensaje);

        return rejected;
    }

    public Reservation cancelReservation(Integer id){
        Reservation aux= reservationRepository.findById(id).orElseThrow(()->new IdNotFoundException ("Reservation", id));
        aux.setStatus(ReservationStatus.CANCELLED);
        Reservation cancelled = reservationRepository.save(aux);

        // Notificar al dueño del espacio que la reserva fue cancelada
        String mensaje = "La reserva de " + aux.getConsumer().getFirstname() + " " + aux.getConsumer().getLastname() +
                " en " + aux.getSpace().getNameSpace() + " ha sido cancelada.";
        notificationService.createNotification(aux.getSpace().getConsumerOwner(), mensaje);

        return cancelled;
    }

    public Reservation completeReservation(Integer id){
        Reservation aux= reservationRepository.findById(id).orElseThrow(()->new IdNotFoundException ("Reservation", id));
        aux.setStatus(ReservationStatus.COMPLETED);
        return reservationRepository.save(aux);
    }

    public Reservation softDelete(Integer id){
        Reservation aux= reservationRepository.findById(id).orElseThrow(()->new IdNotFoundException ("Reservation", id));
        aux.setIsActive(false);
        return reservationRepository.save(aux);

    }
}
