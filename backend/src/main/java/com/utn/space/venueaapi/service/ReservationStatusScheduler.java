package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.model.ReservationStatus;
import com.utn.space.venueaapi.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationStatusScheduler {
    private final ReservationRepository reservationRepository;

    @Scheduled(fixedRate = 60_000)
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

        if (started > 0 || completed > 0) {
            log.info("Estados de reservas actualizados: {} iniciadas, {} completadas",
                    started, completed);
        }
    }
}