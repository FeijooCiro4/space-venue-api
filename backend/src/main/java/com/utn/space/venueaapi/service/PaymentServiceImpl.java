package com.utn.space.venueaapi.service;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import com.utn.space.venueaapi.exceptions.IdNotFoundException;
import com.utn.space.venueaapi.config.ClientRedirectProperties;
import com.utn.space.venueaapi.model.PaymentModel;
import com.utn.space.venueaapi.model.Reservation;
import com.utn.space.venueaapi.model.ReservationStatus;
import com.utn.space.venueaapi.repository.PaymentRepository;
import com.utn.space.venueaapi.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements IPaymentService {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final ClientRedirectProperties clientRedirectProperties;

    @Override
    public String createPreference(Reservation reservation) throws MPException, MPApiException {
        try {
            PreferenceClient client = new PreferenceClient();

            // Crear el ítem (el espacio reservado)
            PreferenceItemRequest itemRequest = PreferenceItemRequest.builder()
                    .id(reservation.getId().toString())
                    .title("Reserva de Espacio: " + reservation.getSpace().getNameSpace())
                    .description("Pago de reserva nro: " + reservation.getId())
                    .quantity(1)
                    .currencyId("ARS") // O tu moneda local compatible con MP
                    .unitPrice(reservation.getFinalPrice())
                    .build();

            List<PreferenceItemRequest> items = new ArrayList<>();
            items.add(itemRequest);

            // Configurar las URLs de retorno (Back Urls)
            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(clientRedirectProperties.getPaymentSuccessUrl())
                    .failure(clientRedirectProperties.getPaymentFailureUrl())
                    .pending(clientRedirectProperties.getPaymentPendingUrl())
                    .build();

            // Construir la preferencia de Mercado Pago
            PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                    .items(items)
                    .backUrls(backUrls)
                    .autoReturn("approved") // Retorno automático al destino configurado si se aprueba
                    .externalReference(reservation.getId().toString()) // Enlace clave con la BD
                    .build();

            Preference preference = client.create(preferenceRequest);

            log.info("✅ Preferencia de Mercado Pago creada exitosamente. Init Point: {}", preference.getInitPoint());

            // Retornamos la URL de inicio del pago
            return preference.getInitPoint();
        } catch (MPException | MPApiException e) {
            log.error("❌ Error de Mercado Pago al crear la preferencia: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("❌ Error inesperado al crear la preferencia: {}", e.getMessage(), e);
            throw new MPException("Error al crear preferencia de pago: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void processNotification(Long paymentId) {
        try {
            PaymentClient client = new PaymentClient();
            // Traemos el recurso desde el SDK de Mercado Pago
            com.mercadopago.resources.payment.Payment mpPayment = client.get(paymentId);

            // Buscamos si ya registramos este pago previamente para actualizarlo (ej. pasó de PENDING a APPROVED)
            PaymentModel paymentEntity = paymentRepository.findById(mpPayment.getId())
                    .orElse(new PaymentModel());

            paymentEntity.setIdPayment(mpPayment.getId());

            // Asociamos la reserva usando el external_reference que mandamos en la preferencia
            Integer reservationId = Integer.valueOf(mpPayment.getExternalReference());
            Reservation reservationRef = reservationRepository.getReferenceById(reservationId);
            paymentEntity.setReservation(reservationRef);

            // Mapeo de campos según tu modelo de datos
            paymentEntity.setPaymentMethodType(mpPayment.getPaymentTypeId());
            paymentEntity.setPaymentMethodId(mpPayment.getPaymentMethodId());
            paymentEntity.setStatus(mpPayment.getStatus());
            paymentEntity.setStatusDetail(mpPayment.getStatusDetail());
            paymentEntity.setCurrencyCode(mpPayment.getCurrencyId());
            paymentEntity.setTransactionAmount(mpPayment.getTransactionAmount());
            paymentEntity.setTotalRefundedAmount(mpPayment.getTransactionAmountRefunded());
            paymentEntity.setExternalReference(mpPayment.getExternalReference());

            if (mpPayment.getTransactionDetails() != null) {
                paymentEntity.setNetReceivedAmount(mpPayment.getTransactionDetails().getNetReceivedAmount());
            }

            // Recuperamos la entidad real para mutar su estado
            Reservation actualReservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new IdNotFoundException("Reservation", reservationId));

            if ("approved".equals(mpPayment.getStatus())) {
                // Si Mercado Pago aprueba el dinero, consolidamos la reserva
                actualReservation.setStatus(ReservationStatus.CONFIRMED);
            } else if ("rejected".equals(mpPayment.getStatus())) {
                // Si el pago es rechazado por falta de fondos, fraude, etc.
                actualReservation.setStatus(ReservationStatus.CANCELLED);
            }
            reservationRepository.save(actualReservation);

            paymentRepository.save(paymentEntity);
            log.info("Pago nro {} procesado exitosamente con estado: {}", paymentId, mpPayment.getStatus());

        } catch (Exception e) {
            log.error("Error al procesar la notificación de Mercado Pago para el ID: {}", paymentId, e);
        }
    }

    @Override
    @Transactional
    public void processMockNotification(Long paymentId, Integer reservationId) {
        try {
            log.info("🎮 Ejecutando SIMULACIÓN LOCAL de pago para Reserva ID: {}", reservationId);

            // Buscamos o creamos el modelo de pago local
            PaymentModel paymentEntity = paymentRepository.findById(paymentId)
                    .orElse(new PaymentModel());

            Reservation actualReservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new IdNotFoundException("Reservation", reservationId));

            // Llenamos los datos simulando que Mercado Pago nos dio el OK
            paymentEntity.setIdPayment(paymentId);
            paymentEntity.setReservation(actualReservation);
            paymentEntity.setPaymentMethodType("credit_card");
            paymentEntity.setPaymentMethodId("visa");
            paymentEntity.setStatus("approved"); // Simulamos aprobación directa
            paymentEntity.setStatusDetail("accredited");
            paymentEntity.setCurrencyCode("ARS");
            paymentEntity.setTransactionAmount(actualReservation.getFinalPrice());
            paymentEntity.setNetReceivedAmount(actualReservation.getFinalPrice());
            paymentEntity.setExternalReference(reservationId.toString());

            // Mutamos el estado de la reserva tal como lo haría el flujo real
            actualReservation.setStatus(ReservationStatus.CONFIRMED);

            reservationRepository.save(actualReservation);
            paymentRepository.save(paymentEntity);

            log.info("🎮 Simulación completada. Reserva #{} CONFIRMADA en Base de Datos.", reservationId);

        } catch (Exception e) {
            log.error("Error en la simulación del pago", e);
        }
    }
}