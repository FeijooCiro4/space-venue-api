package com.utn.space.venueaapi.security;

import com.utn.space.venueaapi.repository.ConsumerRepository;
import com.utn.space.venueaapi.repository.ReservationRepository;
import com.utn.space.venueaapi.repository.SpaceRepository;
import com.utn.space.venueaapi.repository.SpaceImageRepository;
import com.utn.space.venueaapi.repository.ServiceSelectedRepository;
import com.utn.space.venueaapi.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("securityUtils")
public class SecurityUtils {

    @Autowired
    private final ReservationRepository reservationRepository;

    @Autowired
    private final ConsumerRepository consumerRepository;

    private final SpaceRepository spaceRepository;
    private final SpaceImageRepository spaceImageRepository;
    private final ServiceSelectedRepository serviceSelectedRepository;
    private final NotificationRepository notificationRepository;

    public SecurityUtils(ReservationRepository reservationRepository, ConsumerRepository consumerRepository,
                         SpaceRepository spaceRepository, SpaceImageRepository spaceImageRepository,
                         ServiceSelectedRepository serviceSelectedRepository, NotificationRepository notificationRepository) {
        this.reservationRepository = reservationRepository;
        this.consumerRepository = consumerRepository;
        this.spaceRepository = spaceRepository;
        this.spaceImageRepository = spaceImageRepository;
        this.serviceSelectedRepository = serviceSelectedRepository;
        this.notificationRepository = notificationRepository;
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
        if (consumerId == null) return false;
        return consumerRepository.findById(consumerId)
                .map(consumer -> consumer.getCredentials().getUsername().equals(username))
                .orElse(false);
    }

    public boolean canReadReservation(Integer reservationId, String username) {
        return isConsumerOfReservation(reservationId, username)
                || isSpaceOwnerOfReservation(reservationId, username);
    }

    public boolean isSpaceOwner(Integer spaceId, String username) {
        if (spaceId == null) return false;
        return spaceRepository.findById(spaceId)
                .map(space -> space.getConsumerOwner().getCredentials().getUsername().equals(username))
                .orElse(false);
    }

    public boolean isSpaceImageOwner(Integer imageId, String username) {
        return spaceImageRepository.findById(imageId)
                .map(image -> image.getSpace().getConsumerOwner().getCredentials().getUsername().equals(username))
                .orElse(false);
    }

    public boolean isSelectedServiceConsumer(Integer serviceId, String username) {
        return serviceSelectedRepository.findById(serviceId)
                .map(service -> service.getReservation().getConsumer().getCredentials().getUsername().equals(username))
                .orElse(false);
    }

    public boolean isNotificationConsumer(Integer notificationId, String username) {
        return notificationRepository.findById(notificationId)
                .map(notification -> notification.getConsumer().getCredentials().getUsername().equals(username))
                .orElse(false);
    }
}
