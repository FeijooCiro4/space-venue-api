package com.utn.space.venueaapi.controllers;

import com.utn.space.venueaapi.model.records.ErrorResponseDTO;
import com.utn.space.venueaapi.service.IPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Pagos", description = "Notificaciones y eventos relacionados con pagos.")
@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final IPaymentService paymentService;

    @PostMapping("/webhook")
    @Operation(
            summary = "Recibe una notificación de pago de Mercado Pago",
            description = """
                Acepta topic e id como parámetros de consulta o un JSON con type y data.id.
                La ruta actualmente requiere autenticación JWT según SecurityConfig.
                """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificación recibida; respuesta sin cuerpo"),
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> receiveWebhook(
            @Parameter(description = "Tipo del evento", example = "payment")
            @RequestParam(value = "topic", required = false) String topic,

            @Parameter(description = "ID del pago", example = "123456789")
            @RequestParam(value = "id", required = false) Long id,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = false,
                    content = @Content(
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(
                                    value = """
                                {
                                  "type": "payment",
                                  "data": {
                                    "id": "123456789"
                                  }
                                }
                                """
                            )
                    )
            )
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