package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.config.AppClock;
import com.utn.space.venueaapi.model.ECancellationReason;
import com.utn.space.venueaapi.model.PaymentModel;
import com.utn.space.venueaapi.model.Reservation;
import com.utn.space.venueaapi.model.records.CancellationRefundDTO;
import com.utn.space.venueaapi.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

// Política de cancelación global (reemplaza a las políticas por espacio).
// Solo hay reembolso si la reserva tiene un pago aprobado:
//   - Cancelada por el dueño o la plataforma -> 100%
//   - Cancelada por el cliente: faltando >= 7 días -> 100%, >= 48 h -> 50%, < 48 h -> 0%
@Service
@RequiredArgsConstructor
public class CancellationRefundService {
    private final PaymentRepository paymentRepository;

    // Lo que correspondería si la reserva se cancelara ahora por ese motivo. No modifica nada
    public CancellationRefundDTO preview(Reservation reservation, ECancellationReason reason) {
        PaymentModel payment = paymentRepository.findByReservation_Id(reservation.getId());
        if (payment == null || !"approved".equals(payment.getStatus())) {
            return new CancellationRefundDTO(false, 0, BigDecimal.ZERO);
        }
        BigDecimal paid = payment.getTransactionAmount() != null ? payment.getTransactionAmount() : reservation.getFinalPrice();
        return calculate(reason, paid, reservation.getFromDate(), AppClock.now());
    }

    // Calcula el reembolso y lo guarda en la reserva (no la persiste: lo hace quien cancela)
    public CancellationRefundDTO applyRefundToReservation(Reservation reservation, ECancellationReason reason) {
        CancellationRefundDTO result = preview(reservation, reason);
        if (result.paid()) {
            reservation.setRefundPercentage(result.refundPercentage());
            reservation.setRefundAmount(result.refundAmount());
            // El reembolso todavía no se hace automáticamente: queda marcado como pendiente
            reservation.setRefundPending(result.refundAmount().signum() > 0);
        }
        return result;
    }

    private static CancellationRefundDTO calculate(ECancellationReason reason, BigDecimal paid,
                                           LocalDateTime fromDate, LocalDateTime now) {
        int percentage = refundPercentage(reason, Duration.between(now, fromDate));
        BigDecimal refund = paid.multiply(BigDecimal.valueOf(percentage))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new CancellationRefundDTO(true, percentage, refund);
    }

    private static int refundPercentage(ECancellationReason reason, Duration untilStart) {
        if (reason != ECancellationReason.CLIENT) return 100;
        if (untilStart.compareTo(Duration.ofDays(7)) >= 0) return 100;
        if (untilStart.compareTo(Duration.ofHours(48)) >= 0) return 50;
        return 0;
    }

    // Frase para el cliente sobre su reembolso (empieza con espacio para concatenar). Vacía si no había pagado
    public static String clientNotice(CancellationRefundDTO refund) {
        if (!refund.paid()) return "";
        if (refund.refundPercentage() == 0) return " Según la política de cancelación, no corresponde reembolso.";
        return " Se te va a reintegrar el " + refund.refundPercentage() + "% de lo pagado (" + money(refund.refundAmount()) + ").";
    }

    // Frase para el dueño cuando cancela el cliente. Vacía si no había pagado
    public static String ownerNotice(CancellationRefundDTO refund) {
        if (!refund.paid()) return "";
        return " Al cliente se le reintegra el " + refund.refundPercentage() + "% (" + money(refund.refundAmount()) + ").";
    }

    private static String money(BigDecimal amount) {
        return String.format(Locale.forLanguageTag("es-AR"), "$%,.2f", amount);
    }
}
