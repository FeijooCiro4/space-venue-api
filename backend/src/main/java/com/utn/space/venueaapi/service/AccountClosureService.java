package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.exceptions.InvalidReservationException;
import com.utn.space.venueaapi.exceptions.NameNotFoundException;
import com.utn.space.venueaapi.model.*;
import com.utn.space.venueaapi.model.records.AccountImpactDTO;
import com.utn.space.venueaapi.repository.ConsumerRepository;
import com.utn.space.venueaapi.repository.ReservationRepository;
import com.utn.space.venueaapi.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// Bajas de cuentas y cambio de rol a cliente, con el cierre de las reservas que dependen de esa cuenta.
// Los espacios del dueño no se modifican: quedan ocultos por Space.isOwnerAvailable() y vuelven solos si se reactiva.
// Todo corre en una transacción: si algo se rechaza (por ej. una reserva en curso) no queda nada a medias
@Service
@RequiredArgsConstructor
public class AccountClosureService {
    private final ConsumerRepository consumerRepository;
    private final SpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationClosureService reservationClosureService;

    private static final List<ReservationStatus> CANCELABLES = List.of(ReservationStatus.TENTATIVE, ReservationStatus.CONFIRMED);

    // Baja hecha por un admin (DELETE /usuarios/{id} y PUT /usuarios/{id}/status?active=false)
    @Transactional
    public void deactivateByAdmin(Integer id, String requesterUsername) {
        Consumer consumer = consumerRepository.findById(id)
                .orElseThrow(() -> new NameNotFoundException("No se encontró el usuario."));
        Credential credential = requireCredential(consumer);
        if (credential.getUsername().equals(requesterUsername)) {
            throw new InvalidDataException("No podés desactivar tu propia cuenta.");
        }
        requireNothingInProgress(consumer);

        closeOwnedSpaces(consumer, ECancellationReason.PLATFORM, "un administrador dio de baja la cuenta del dueño.");
        // Como cliente: se cancelan todas sus reservas futuras; las pagadas quedan pendientes de reintegro
        for (Reservation r : futureClientReservations(consumer)) {
            reservationClosureService.cancelForClientClosure(r, ECancellationReason.PLATFORM,
                    "un administrador dio de baja la cuenta del cliente.");
        }

        credential.setIsActive(false);
        credential.setDeactivationReason(EDeactivationReason.ADMIN);
        consumerRepository.save(consumer);
    }

    // Baja pedida por el propio usuario (DELETE /usuario)
    @Transactional
    public void deactivateBySelf(String username) {
        Consumer consumer = consumerRepository.findByUsername(username)
                .orElseThrow(() -> new NameNotFoundException("No se encontró el usuario."));
        Credential credential = requireCredential(consumer);
        requireNothingInProgress(consumer);

        List<Reservation> comoCliente = futureClientReservations(consumer);
        // Las pagadas no se cancelan solas: el usuario tiene que cancelarlas antes (ahí aplica la política de cancelación)
        long pagadas = comoCliente.stream().filter(reservationClosureService::isPaid).count();
        if (pagadas > 0) {
            throw new InvalidDataException(pagadas == 1
                    ? "Tenés una reserva pagada que todavía no empezó. Cancelala antes de dar de baja tu cuenta."
                    : "Tenés " + pagadas + " reservas pagadas que todavía no empezaron. Cancelalas antes de dar de baja tu cuenta.");
        }

        closeOwnedSpaces(consumer, ECancellationReason.OWNER, "el dueño dio de baja su cuenta.");
        for (Reservation r : comoCliente) {
            reservationClosureService.cancelForClientClosure(r, ECancellationReason.CLIENT, "el cliente dio de baja su cuenta.");
        }

        credential.setIsActive(false);
        credential.setDeactivationReason(EDeactivationReason.USER_REQUEST);
        consumerRepository.save(consumer);
    }

    // Cambio de rol entre CLIENT y OFFERER hecho por un admin. No se pueden dar ni quitar permisos de ADMIN por este medio.
    // Al pasar de OFFERER a CLIENT sus espacios quedan ocultos y se cancelan sus reservas futuras
    @Transactional
    public void changeRol(Integer id, ERoles rol) {
        Consumer consumer = consumerRepository.findById(id)
                .orElseThrow(() -> new NameNotFoundException("No se encontró el usuario."));
        Credential credential = requireCredential(consumer);
        if (rol != ERoles.ROLE_CLIENT && rol != ERoles.ROLE_OFFERER) {
            throw new InvalidDataException("Solo se puede asignar el rol de cliente u oferente.");
        }
        if (credential.getRol() == ERoles.ROLE_ADMIN) {
            throw new InvalidDataException("No se puede cambiar el rol de un administrador.");
        }
        if (rol == ERoles.ROLE_CLIENT && credential.getRol() == ERoles.ROLE_OFFERER) {
            // closeFutureReservations rechaza la operación si algún espacio tiene una reserva en curso
            closeOwnedSpaces(consumer, ECancellationReason.PLATFORM, "el dueño ya no puede publicar espacios.");
        }
        credential.setRol(rol);
        consumerRepository.save(consumer);
    }

    public enum ImpactAction { DESACTIVAR, CAMBIAR_A_CLIENTE }

    // Calcula qué pasaría con deactivateByAdmin o con changeRol a CLIENT, sin cambiar nada.
    // Usa las mismas verificaciones que esas operaciones, así el resumen coincide con lo que después se hace
    @Transactional(readOnly = true)
    public AccountImpactDTO impact(Integer id, ImpactAction accion, String requesterUsername) {
        Consumer consumer = consumerRepository.findById(id)
                .orElseThrow(() -> new NameNotFoundException("No se encontró el usuario."));
        Credential credential = requireCredential(consumer);
        boolean desactivar = accion == ImpactAction.DESACTIVAR;

        String motivoBloqueo = null;
        try {
            if (desactivar) {
                if (credential.getUsername().equals(requesterUsername)) {
                    throw new InvalidDataException("No podés desactivar tu propia cuenta.");
                }
                requireNothingInProgress(consumer);
            } else {
                if (credential.getRol() == ERoles.ROLE_ADMIN) {
                    throw new InvalidDataException("No se puede cambiar el rol de un administrador.");
                }
                for (Space space : spaceRepository.findAllByConsumerOwnerIdConsumer(id)) {
                    reservationClosureService.checkCanClose(space);
                }
            }
        } catch (InvalidDataException | InvalidReservationException e) {
            motivoBloqueo = e.getMessage();
        }

        // Al pasar a cliente solo cambia algo si hoy es oferente
        boolean afectaEspacios = desactivar || credential.getRol() == ERoles.ROLE_OFFERER;
        List<Space> espacios = afectaEspacios ? spaceRepository.findAllByConsumerOwnerIdConsumer(id) : List.of();

        List<Reservation> aCancelar = new ArrayList<>();
        for (Space space : espacios) {
            reservationRepository.findAllBySpace_IdSpace(space.getIdSpace()).stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                    .filter(r -> CANCELABLES.contains(r.getStatus()))
                    .forEach(aCancelar::add);
        }
        if (desactivar) aCancelar.addAll(futureClientReservations(consumer));

        // Solo se cuentan los espacios que hoy se ven: los eliminados o desactivados ya estaban ocultos
        int ocultos = (int) espacios.stream().filter(Space::isBookable).count();
        int pagadas = (int) aCancelar.stream().filter(reservationClosureService::isPaid).count();
        return new AccountImpactDTO(ocultos, aCancelar.size(), pagadas, motivoBloqueo != null, motivoBloqueo);
    }

    private void closeOwnedSpaces(Consumer owner, ECancellationReason reason, String motivo) {
        for (Space space : spaceRepository.findAllByConsumerOwnerIdConsumer(owner.getIdConsumer())) {
            reservationClosureService.closeFutureReservations(space, reason, motivo);
        }
    }

    private List<Reservation> futureClientReservations(Consumer consumer) {
        return reservationRepository.findAllByConsumer_IdConsumer(consumer.getIdConsumer()).stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .filter(r -> CANCELABLES.contains(r.getStatus()))
                .toList();
    }

    // Una cuenta con una reserva en curso (como dueño o como cliente) no se puede dar de baja
    private void requireNothingInProgress(Consumer consumer) {
        boolean comoCliente = reservationRepository.findAllByConsumer_IdConsumer(consumer.getIdConsumer()).stream()
                .anyMatch(r -> Boolean.TRUE.equals(r.getIsActive()) && r.getStatus() == ReservationStatus.IN_PROGRESS);
        if (comoCliente) {
            throw new InvalidDataException("La cuenta tiene una reserva en curso como cliente. Esperá a que termine para darla de baja.");
        }
        // Como dueño lo verifica closeFutureReservations, pero se chequea antes para no hacer trabajo de más
        for (Space space : spaceRepository.findAllByConsumerOwnerIdConsumer(consumer.getIdConsumer())) {
            reservationClosureService.checkCanClose(space);
        }
    }

    private Credential requireCredential(Consumer consumer) {
        if (consumer.getCredentials() == null) {
            throw new NameNotFoundException("No se encontraron credenciales para este usuario.");
        }
        return consumer.getCredentials();
    }
}
