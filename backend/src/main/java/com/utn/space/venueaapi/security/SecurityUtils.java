package com.utn.space.venueaapi.security;

import com.utn.space.venueaapi.repository.ConsumerRepository;
import com.utn.space.venueaapi.repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("securityUtils")
public class SecurityUtils {

    @Autowired
    private final ReservationRepository reservationRepository;

    @Autowired
    private final ConsumerRepository consumerRepository;

    public SecurityUtils(ReservationRepository reservationRepository, ConsumerRepository consumerRepository) {
        this.reservationRepository = reservationRepository;
        this.consumerRepository = consumerRepository;
    }

    public boolean isSpaceOwnerOfReservation(Integer reservationId, String username) {
        return reservationRepository.findById(reservationId)
                .map(reservation -> reservation.getSpace().getConsumerOwner().getCredentials().getUsername().equals(username))
                .orElse(false);
    }

    public boolean isConsumerOfReservation(Integer reservationId, String username) {
        return reservationRepository.findById(reservationId)
                .map(reservation -> reservation.getConsumer().getCredentials().getUsername().equals(username))
                .orElse(false);
    }

    public boolean isReservationOwner(Integer reservationId, String username) {
        return reservationRepository.findById(reservationId)
                .map(reservation -> reservation.getConsumer().getCredentials().getUsername().equals(username))
                .orElse(false);
    }

    public boolean isCurrentConsumer(Integer consumerId, String username) {
        return consumerRepository.findById(consumerId)
                .map(consumer -> consumer.getCredentials().getUsername().equals(username))
                .orElse(false);
    }
}
