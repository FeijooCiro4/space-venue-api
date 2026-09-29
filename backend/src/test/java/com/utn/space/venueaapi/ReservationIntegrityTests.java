package com.utn.space.venueaapi;

import com.utn.space.venueaapi.exceptions.*;
import com.utn.space.venueaapi.model.*;
import com.utn.space.venueaapi.model.records.ReservationDTO;
import com.utn.space.venueaapi.repository.*;
import com.utn.space.venueaapi.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReservationIntegrityTests {
    @Autowired ReservationService reservations;
    @Autowired ReservationRepository reservationRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ConsumerRepository consumers;
    @Autowired SpaceRepository spaces;
    @Autowired SpaceServiceItemRepository catalog;
    Consumer client, owner;
    Space space;
    Reservation reservation;
    LocalDateTime start;

    @BeforeEach
    void setup() {
        start = LocalDateTime.now().plusDays(3).withNano(0);
        client = consumers.saveAndFlush(new Consumer());
        owner = consumers.saveAndFlush(new Consumer());
        space = space(owner);
        reservation = booking(space, ReservationStatus.TENTATIVE, start);
    }

    @Test
    void editRetainsServerFieldsAndRecalculatesCatalogPrice() {
        SpaceServiceItem extra = catalog.saveAndFlush(new SpaceServiceItem(null, "Proyector",
                new BigDecimal("250.00"), true, space));
        var createdAt = reservation.getCreatedAt();
        Reservation result = reservations.modify(dto(space, client, start, List.of(extra.getId())));
        assertEquals(reservation.getId(), result.getId());
        assertEquals(client.getIdConsumer(), result.getConsumer().getIdConsumer());
        assertEquals(createdAt, result.getCreatedAt());
        assertEquals(ReservationStatus.TENTATIVE, result.getStatus());
        assertTrue(result.getIsActive());
        assertEquals(0, new BigDecimal("2250.00").compareTo(result.getFinalPrice()));
        assertEquals("Proyector", result.getServices().get(0).getDescriptionFrozen());
        reservationRepository.flush();
    }

    @Test
    void editRejectsChangingConsumerEvenForServiceCall() {
        assertThrows(InvalidReservationException.class,
                () -> reservations.modify(dto(space, owner, start, List.of())));
    }

    @Test
    void editExcludesItselfButRejectsOverlappingReservation() {
        reservations.modify(dto(space, client, start, List.of()));
        booking(space, ReservationStatus.TENTATIVE, start.plusDays(1));
        assertThrows(InvalidReservationException.class,
                () -> reservations.modify(dto(space, client, start.plusDays(1), List.of())));
        assertEquals(start, reservation.getFromDate());
    }

    @Test
    void editRejectsOwnSpace() {
        Space own = space(client);
        assertThrows(SelfReservationException.class,
                () -> reservations.modify(dto(own, client, start, List.of())));
    }

    @Test
    void editRejectsInactiveSpaceAndPastDates() {
        space.setIsActive(false);
        spaces.saveAndFlush(space);
        assertThrows(InvalidReservationException.class,
                () -> reservations.modify(dto(space, client, start, List.of())));
    }

    @Test
    void editRejectsPastDates() {
        assertThrows(InvalidDateException.class,
                () -> reservations.modify(dto(space, client, start.minusDays(5), List.of())));
    }

    @Test
    void editRechecksLimitInDestinationSpace() {
        Space target = space(owner);
        for (int i = 1; i <= 5; i++) booking(target, ReservationStatus.COMPLETED, start.minusDays(10 + i));
        assertThrows(ReservationLimitException.class,
                () -> reservations.modify(dto(target, client, start, List.of())));
    }

    @Test
    void editRejectsServicesFromAnotherSpace() {
        SpaceServiceItem extra = catalog.saveAndFlush(new SpaceServiceItem(null, "Extra",
                BigDecimal.TEN, true, space(owner)));
        assertThrows(ServiceOutOfPlaceException.class,
                () -> reservations.modify(dto(space, client, start, List.of(extra.getId()))));
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = "TENTATIVE", mode = EnumSource.Mode.EXCLUDE)
    void nonTentativeReservationsCannotBeEdited(ReservationStatus state) {
        state(state);
        assertThrows(InvalidReservationException.class,
                () -> reservations.modify(dto(space, client, start, List.of())));
    }

    @Test
    void registeredPaymentPreventsEditing() {
        storedPayment("pending", BigDecimal.ZERO);
        assertThrows(InvalidReservationException.class,
                () -> reservations.modify(dto(space, client, start, List.of())));
    }

    private void storedPayment(String status, BigDecimal refunded) {
        PaymentModel payment = new PaymentModel();
        payment.setIdPayment(20L);
        payment.setReservation(reservation);
        payment.setStatus(status);
        payment.setCurrencyCode("ARS");
        payment.setTransactionAmount(reservation.getFinalPrice());
        payment.setTotalRefundedAmount(refunded);
        paymentRepository.saveAndFlush(payment);
    }

    private void state(ReservationStatus status) {
        reservation.setStatus(status);
        reservationRepository.saveAndFlush(reservation);
    }

    private ReservationDTO dto(Space target, Consumer consumer, LocalDateTime from, List<Integer> extras) {
        return new ReservationDTO(reservation.getId(), "Editada", "Edición de prueba", from,
                from.plusHours(2), 0.01, ReservationStatus.COMPLETED, start.minusYears(2), false,
                consumer.getIdConsumer(), target.getIdSpace(), extras);
    }

    private Space space(Consumer owner) {
        Space result = new Space();
        result.setConsumerOwner(owner);
        result.setNameSpace("Sala");
        result.setBasePrice(new BigDecimal("1000.00"));
        result.setBufferTime(30);
        result.setIsActive(true);
        result.setServices(new ArrayList<>());
        return spaces.saveAndFlush(result);
    }

    private Reservation booking(Space target, ReservationStatus status, LocalDateTime from) {
        Reservation result = new Reservation();
        result.setConsumer(client);
        result.setSpace(target);
        result.setTitle("Reserva");
        result.setDescription("Prueba");
        result.setCreatedAt(start.minusDays(3));
        result.setFromDate(from);
        result.setUntilDate(from.plusHours(2));
        result.setFinalPrice(new BigDecimal("2000.00"));
        result.setStatus(status);
        result.setServices(new ArrayList<>());
        return reservationRepository.saveAndFlush(result);
    }
}
