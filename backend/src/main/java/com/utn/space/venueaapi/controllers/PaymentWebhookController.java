package com.utn.space.venueaapi.controllers;

import com.utn.space.venueaapi.service.IPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final IPaymentService paymentService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveWebhook(
            @RequestParam(value = "topic", required = false) String topic,
            @RequestParam(value = "id", required = false) Long id,
            @RequestBody(required = false) Map<String, Object> data) {

        String currentTopic = topic;
        Long currentId = id;

        if (data != null) {
            if (data.containsKey("type")) {
                currentTopic = (String) data.get("type");
            } else if (data.containsKey("action")) {
                currentTopic = "payment"; // Mapeo por descarte de acción
            }

            if (data.containsKey("data")) {
                Map<String, Object> dataContent = (Map<String, Object>) data.get("data");
                if (dataContent != null && dataContent.containsKey("id")) {
                    currentId = Long.parseLong(String.valueOf(dataContent.get("id")));
                }
            }
        }

        if ("payment".equals(currentTopic) && currentId != null) {
            log.info("Webhook recibido para el pago ID: {}", currentId);

            // Si el body contiene un flag de simulación, manejamos datos de prueba
            boolean isSimulation = data != null && data.containsKey("isSimulation");
            if (isSimulation) {
                // Se pasa un ID de reserva ficticio o dinámico
                Integer resId = data.containsKey("reservationId") ? Integer.parseInt(String.valueOf(data.get("reservationId"))) : 1;
                paymentService.processMockNotification(currentId, resId);
            } else {
                // Flujo real con tokens del Sandbox conectándose a internet
                paymentService.processNotification(currentId);
            }
        }

        return ResponseEntity.ok().build();
    }
}