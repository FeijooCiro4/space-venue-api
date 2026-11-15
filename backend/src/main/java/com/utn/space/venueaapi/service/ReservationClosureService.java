package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.InvalidReservationException;
import com.utn.space.venueaapi.model.ECancellationReason;
import com.utn.space.venueaapi.model.Reservation;
import com.utn.space.venueaapi.model.ReservationStatus;
import com.utn.space.venueaapi.model.Space;
import com.utn.space.venueaapi.repository.PaymentRepository;
import com.utn.space.venueaapi.repository.ReservationRepository;
import com.utn.space.venueaapi.repository.SpaceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Cierra las reservas pendientes de un espacio cuando deja de poder usarse (baja del espacio,
// baja del dueño o cambio de rol). Es la regla común de todas las bajas.
// Está separado de ReservationService para que SpaceService pueda usarlo sin dependencia circular.
@Service
@RequiredArgsConstructor
public class ReservationClosureService {
    private final ReservationRepository reservationRepository;
    private final PaymentRepository paymentRepository;
    private final SpaceRepository spaceRepository;
    private final NotificationService notificationService;
    private final EntityManager entityManager;

    // Estados que todavía no empezaron y se pueden cancelar
    private static final List<ReservationStatus> CANCELABLES = List.of(ReservationStatus.TENTATIVE, ReservationStatus.CONFIRMED);

    // Verifica que se puedan cerrar las reservas del espacio sin cambiar nada.
    // Lanza InvalidReservationException si hay una reserva en curso o una pagada (hasta que haya reembolsos)
    public void checkCanClose(Space space) {
        List<Reservation> reservas = reservationRepository.findAllBySpace_IdSpace(space.getIdSpace()).stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .toList();
        if (reservas.stream().anyMatch(r -> r.getStatus() == ReservationStatus.IN_PROGRESS)) {
            throw new InvalidReservationException("El espacio \"" + space.getNameSpace()
                    + "\" tiene una reserva en curso. Esperá a que termine para darlo de baja.");
        }
        // TODO Parte 3: reembolsar en lugar de bloquear
        if (reservas.stream().anyMatch(r -> r.getStatus() == ReservationStatus.CONFIRMED && isPaid(r))) {
            throw new InvalidReservationException("El espacio \"" + space.getNameSpace()
                    + "\" tiene reservas pagadas. Por ahora no se puede dar de baja hasta que esas reservas se cancelen o terminen.");
        }
    }

    // Cancela las reservas TENTATIVE y CONFIRMED del espacio y avisa a cada cliente.
    // Las COMPLETED, CANCELLED y REJECTED no se tocan. Si hay alguna en curso (o pagada) no cambia nada.
    // Debe llamarse dentro de la transacción de la baja, así si algo falla no queda nada a medias.
    // Devuelve cuántas reservas canceló
    @Transactional(propagation = Propagation.MANDATORY)
    public int closeFutureReservations(Space space, ECancellationReason reason, String motivo) {
        // Bloquear el espacio frena reservas nuevas mientras se cierran las existentes (create también lo bloquea)
        Space locked = spaceRepository.findByIdForUpdate(space.getIdSpace()).orElse(space);
        checkCanClose(locked);

        List<Reservation> aCancelar = reservationRepository.findAllBySpace_IdSpace(locked.getIdSpace()).stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .filter(r -> CANCELABLES.contains(r.getStatus()))
                .toList();

        for (Reservation r : aCancelar) {
            entityManager.lock(r, LockModeType.PESSIMISTIC_WRITE);
            r.setStatus(ReservationStatus.CANCELLED);
            r.setCancellationReason(reason);
            reservationRepository.save(r);
            notificationService.createNotification(r.getConsumer(),
                    "Tu reserva \"" + r.getTitle() + "\" en " + locked.getNameSpace() + " fue cancelada: " + motivo);
        }
        return aCancelar.size();
    }

    private boolean isPaid(Reservation r) {
        return paymentRepository.existsByReservation_IdAndStatus(r.getId(), "approved");
    }
}
