package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.*;
import com.utn.space.venueaapi.model.*;
import com.utn.space.venueaapi.model.records.ReservationDTO;
import com.utn.space.venueaapi.repository.SpaceServiceItemRepository;
import com.utn.space.venueaapi.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.utn.space.venueaapi.repository.ReservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;

@Slf4j
@Service
public class ReservationService {
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PaymentRepository paymentRepository;

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

    private boolean isSpaceAvailableBetweenDates(LocalDateTime from, LocalDateTime until,
                                                  Space space, Integer excludedReservationId) {
        return reservationRepository.findAllBySpace_IdSpace(space.getIdSpace()).stream()
                .filter(other -> !Objects.equals(other.getId(), excludedReservationId))
                .filter(other -> Boolean.TRUE.equals(other.getIsActive()))
                .filter(other -> other.getStatus() != ReservationStatus.CANCELLED
                        && other.getStatus() != ReservationStatus.REJECTED)
                .noneMatch(other -> !other.getFromDate().isAfter(until.plusMinutes(space.getBufferTime()))
                        && !other.getUntilDate().plusMinutes(space.getBufferTime()).isBefore(from));
    }

    // Usar dentro de una transacción. Refrescar evita reutilizar la entidad leída
    // por @PreAuthorize antes de esperar el bloqueo de otra solicitud.
    public Reservation findByIdForUpdate(Integer id) {
        Reservation reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IdNotFoundException("Reservation", id));
        entityManager.refresh(reservation, LockModeType.PESSIMISTIC_WRITE);
        return reservation;
    }

    private void validateBooking(Consumer consumer, Space space, LocalDateTime from,
                                 LocalDateTime until, Integer excludedReservationId) {
        if (from == null || until == null || !until.isAfter(from) || from.isBefore(LocalDateTime.now())) {
            throw new InvalidDateException("La reserva debe comenzar en el futuro y tener una duración positiva.");
        }
        if (!Boolean.TRUE.equals(space.getIsActive())) {
            throw new InvalidReservationException("El espacio no está activo.");
        }
        if (space.getConsumerOwner().getIdConsumer().equals(consumer.getIdConsumer())) {
            throw new SelfReservationException("No puede reservar su propio espacio.");
        }
        if (!isSpaceAvailableBetweenDates(from, until, space, excludedReservationId)) {
            throw new InvalidReservationException("La reserva no está disponible en las fechas seleccionadas.");
        }
        long count = reservationRepository.countCompletedReservationsByConsumerAndSpace(
                consumer.getIdConsumer(), space.getIdSpace());
        if (count >= 5) {
            throw new ReservationLimitException("Has alcanzado el límite máximo de 5 reservas en este espacio.");
        }
    }

    private void applyDetails(Reservation reservation, ReservationDTO dto, Space space) {
        List<ServiceSelected> selected = new ArrayList<>();
        BigDecimal extras = BigDecimal.ZERO;
        var ids = new HashSet<Integer>();
        if (dto.idServicesSelec() != null) {
            for (Integer id : dto.idServicesSelec()) {
                if (id == null || !ids.add(id)) {
                    throw new InvalidReservationException("Los servicios seleccionados deben tener IDs válidos y no repetidos.");
                }
                SpaceServiceItem item = spaceServiceItemRepository.findById(id)
                        .orElseThrow(() -> new IdNotFoundException("Servicio Catálogo", id));
                if (!item.getSpace().getIdSpace().equals(space.getIdSpace())) {
                    throw new ServiceOutOfPlaceException("El servicio no corresponde al espacio seleccionado.");
                }
                if (!Boolean.TRUE.equals(item.getIsActive())) {
                    throw new InvalidReservationException("El servicio seleccionado no está activo.");
                }
                selected.add(new ServiceSelected(item, reservation));
                extras = extras.add(item.getPrice());
            }
        }
        reservation.setTitle(dto.title());
        reservation.setDescription(dto.description());
        reservation.setFromDate(dto.fromDate());
        reservation.setUntilDate(dto.untilDate());
        reservation.setSpace(space);
        if (reservation.getServices() == null) reservation.setServices(new ArrayList<>());
        reservation.getServices().clear();
        reservation.getServices().addAll(selected);
        long minutes = java.time.temporal.ChronoUnit.MINUTES.between(dto.fromDate(), dto.untilDate());
        reservation.setFinalPrice(space.getBasePrice().multiply(BigDecimal.valueOf(minutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).add(extras));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation create(ReservationDTO dto) {
        Space space = spaceService.findByIdForUpdate(dto.idSpace());
        Consumer client = consumerService.findById(consumerService.getLoggedConsumerId());
        validateBooking(client, space, dto.fromDate(), dto.untilDate(), null);
        // El servidor decide identidad, estado y actividad, incluso si el DTO trae esos campos.
        Reservation reservation = new Reservation();
        reservation.setConsumer(client);
        reservation.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        reservation.setStatus(ReservationStatus.TENTATIVE);
        reservation.setIsActive(true);
        applyDetails(reservation, dto, space);
        Reservation saved = reservationRepository.save(reservation);
        notificationService.createNotification(space.getConsumerOwner(),
                "Nueva reserva de " + client.getFirstname() + " " + client.getLastname()
                        + " para " + space.getNameSpace() + ". Confirmá o rechazá la reserva.");
        return saved;
    }

    public void requireEditable(Reservation reservation) {
        requireState(reservation, ReservationStatus.TENTATIVE);
        if (paymentRepository.existsByReservation_Id(reservation.getId())) {
            throw new InvalidReservationException("No se puede editar una reserva que ya tiene un pago registrado.");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation modify(ReservationDTO dto) {
        Reservation reservation = findByIdForUpdate(dto.id());
        requireEditable(reservation);
        if (!Objects.equals(dto.idConsumer(), reservation.getConsumer().getIdConsumer())) {
            throw new InvalidReservationException("No se puede cambiar el titular de la reserva.");
        }
        Integer oldSpaceId = reservation.getSpace().getIdSpace();
        // Orden estable para dos ediciones que intercambian espacios.
        spaceService.findByIdForUpdate(Math.min(oldSpaceId, dto.idSpace()));
        Space target = spaceService.findByIdForUpdate(Math.max(oldSpaceId, dto.idSpace()));
        if (!target.getIdSpace().equals(dto.idSpace())) target = spaceService.findById(dto.idSpace());
        validateBooking(reservation.getConsumer(), target, dto.fromDate(), dto.untilDate(), reservation.getId());
        applyDetails(reservation, dto, target);
        return reservationRepository.save(reservation);
    }

    private void requireState(Reservation reservation, ReservationStatus... allowed) {
        if (!Boolean.TRUE.equals(reservation.getIsActive()) ||
                java.util.Arrays.stream(allowed).noneMatch(state -> state == reservation.getStatus())) {
            throw new InvalidReservationException("Operación no permitida para el estado actual de la reserva.");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation confirmReservation(Integer id) {
        Reservation reservation = findByIdForUpdate(id);
        requireState(reservation, ReservationStatus.TENTATIVE);
        Space space = spaceService.findByIdForUpdate(reservation.getSpace().getIdSpace());
        validateBooking(reservation.getConsumer(), space, reservation.getFromDate(), reservation.getUntilDate(), id);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        notificationService.createNotification(reservation.getConsumer(),
                "Tu reserva en " + space.getNameSpace() + " ha sido confirmada. ¡Ya puedes pagar!");
        return reservationRepository.save(reservation);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation rejectReservation(Integer id) {
        Reservation reservation = findByIdForUpdate(id);
        requireState(reservation, ReservationStatus.TENTATIVE);
        reservation.setStatus(ReservationStatus.REJECTED);
        notificationService.createNotification(reservation.getConsumer(),
                "Tu reserva en " + reservation.getSpace().getNameSpace() + " ha sido rechazada por el propietario.");
        return reservationRepository.save(reservation);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation cancelReservation(Integer id) {
        Reservation reservation = findByIdForUpdate(id);
        requireState(reservation, ReservationStatus.TENTATIVE, ReservationStatus.CONFIRMED);
        reservation.setStatus(ReservationStatus.CANCELLED);
        notificationService.createNotification(reservation.getSpace().getConsumerOwner(),
                "La reserva de " + reservation.getConsumer().getFirstname() + " " + reservation.getConsumer().getLastname()
                        + " en " + reservation.getSpace().getNameSpace() + " ha sido cancelada.");
        return reservationRepository.save(reservation);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation completeReservation(Integer id) {
        Reservation reservation = findByIdForUpdate(id);
        requireState(reservation, ReservationStatus.CONFIRMED);
        reservation.setStatus(ReservationStatus.COMPLETED);
        return reservationRepository.save(reservation);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation softDelete(Integer id) {
        Reservation reservation = findByIdForUpdate(id);
        reservation.setIsActive(false);
        return reservationRepository.save(reservation);
    }
}
