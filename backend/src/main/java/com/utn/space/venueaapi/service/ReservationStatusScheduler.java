package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.model.ReservationStatus;
import com.utn.space.venueaapi.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationStatusScheduler {
    @Autowired
    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;

    @Scheduled(fixedRate = 10_000)
    @Transactional
    public void updateReservationStatuses() {
        LocalDateTime now = LocalDateTime.now();

        int started = reservationRepository.updateStartedReservations(
                ReservationStatus.CONFIRMED,
                ReservationStatus.IN_PROGRESS,
                now
        );

        int completed = reservationRepository.updateFinishedReservations(
                List.of(ReservationStatus.CONFIRMED, ReservationStatus.IN_PROGRESS),
                ReservationStatus.COMPLETED,
                now
        );

        List<Integer> expiredIds = reservationRepository.findExpiredTentativeReservationIds(
                ReservationStatus.TENTATIVE,
                now
        );

        for (Integer id : expiredIds) {
            reservationService.cancelReservation(id);
        }

        int expired = expiredIds.size();

        if (started > 0 || completed > 0 || expired > 0) {
            log.info("Estados de reservas actualizados: {} iniciadas, {} completadas, {} expiradas",
                    started, completed, expired);
        }
    }
}