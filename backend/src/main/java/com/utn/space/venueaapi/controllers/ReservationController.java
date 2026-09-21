package com.utn.space.venueaapi.controllers;

import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.utn.space.venueaapi.model.Reservation;
import com.utn.space.venueaapi.model.flags.Create;
import com.utn.space.venueaapi.model.flags.Update;
import com.utn.space.venueaapi.model.records.ReservationDTO;
import com.utn.space.venueaapi.security.JwtUtil;
import com.utn.space.venueaapi.service.ConsumerService;
import com.utn.space.venueaapi.service.IPaymentService;
import com.utn.space.venueaapi.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservaciones", description = "Operaciones sobre Reservación.")

public class ReservationController {
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private final IPaymentService paymentService;
    @Autowired
    private final ConsumerService consumerService;
    @Autowired
    private final JwtUtil jwtUtil;

    public ReservationController(IPaymentService paymentService, ConsumerService consumerService, JwtUtil jwtUtil) {
        this.paymentService = paymentService;
        this.consumerService = consumerService;
        this.jwtUtil = jwtUtil;
    }

///---------------------------Metodos------------------------------------------------------------------------------------
    @PostMapping("/{id}/checkout")
    @PreAuthorize("hasRole('CLIENT') and @securityUtils.isReservationOwner(#id, authentication.name)")
    public ResponseEntity<Map<String, String>> createPaymentPreference(@Parameter(description = "ID de la Reservación") @PathVariable Integer id) {
        try {
            Reservation reservation = reservationService.findById(id);

            // Validar que la reserva esté confirmada por el dueño antes de poder pagar
            if (!"CONFIRMED".equals(reservation.getStatus().toString())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "La reserva debe ser confirmada por el dueño del espacio antes de poder pagar."));
            }

            // Invocar al servicio de Mercado Pago para generar el link
            String initPointUrl = paymentService.createPreference(reservation);

            // Retornar la URL de pago en una respuesta JSON
            return ResponseEntity.ok(Collections.singletonMap("initPoint", initPointUrl));

        } catch (MPException | MPApiException e) {
            // Errores específicos del SDK de Mercado Pago (credenciales inválidas, problemas de API, etc.)
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Error desconocido en Mercado Pago";
            org.slf4j.LoggerFactory.getLogger(this.getClass())
                    .error("❌ Error de Mercado Pago para reserva {}: {}", id, errorMsg, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Error al comunicarse con la pasarela de pagos: " + errorMsg));
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(this.getClass())
                    .error("❌ Error inesperado en checkout para reserva {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "Error interno del sistema: " + e.getMessage()));
        }
    }

    @GetMapping
    @Operation(
            summary = "Busca TODAS las Reservas.",
            description = "Devuelve una lista Completa de Reservas."
    )
    public ResponseEntity<List<Reservation>> findAll (){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.findAll());
    }

    @GetMapping("/me")
    @Operation(
            summary = "Busca las reservas del usuario actual en sesión.",
            description = "Devuelve la lista completa de alquileres que le pertenecen al cliente autenticado."
    )
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<List<Reservation>> findAllForCurrentConsumer() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.findAllForLoggedConsumer());
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Busca una Reserva.",
            description = "Busca la ID de una reserva y la devuelve."
    )
    public ResponseEntity<Reservation> findById ( @Parameter(description = "ID de la Reservación") @PathVariable Integer id){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.findById(id));
    }


    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @Operation(
            summary = "Crea una Reserva.",
            description = "El cliente entra un ReservaDTO por el body."
    )
    public ResponseEntity<Reservation> createReservation(
            /*@io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Entra los datos obligatorios de la creacion de una nueva Reserva",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = ReservationDTO.class),
                            examples = @ExampleObject (
                                    name = "Ejemplo",
                                    value = """
                                    {
                                      "id": 4,
                                      "title":"Cumpleaños",
                                      "description":"Cumpleaños de Cecilia",
                                      "fromDate":"2026-09-15T21:00:00",
                                      "untilDate":"2026-09-16T05:00:00",
                                      "status":"CONFIRMED",
                                      "createdAt":"2026-05-20T011:25:31"
                                      "id_consumer":1,
                                      "id_space":2,
                                      "idServicesSelec":[1, 2, 5, 9]}
                                    """)
                    )
            )*/
            @Validated(Create.class)
            @RequestBody ReservationDTO dto,
            //Principal principal
            HttpServletRequest request
    ) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = bearerToken.substring(7);
        Integer consumerIdReal = jwtUtil.extraerConsumerId(token);

        if (consumerIdReal == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Reservation nuevaReserva = reservationService.create(dto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(nuevaReserva);
    }

    @PutMapping
    public ResponseEntity<Reservation> modifyReservation(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Entra los datos obligatorios de la creacion de una nueva Reserva",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = ReservationDTO.class),
                            examples = @ExampleObject (
                                    name = "Ejemplo",
                                    value = """
                                    {
                                      "id": 4,
                                      "title":"Cumpleaños",
                                      "description":"Cumpleaños de Cecilia",
                                      "fromDate":"2026-09-15T21:00:00",
                                      "untilDate":"2026-09-16T05:00:00",
                                      "status":"CONFIRMED",
                                      "createdAt":"2026-05-20T011:25:31"
                                      "id_consumer":1,
                                      "id_space":2,
                                      "idServicesSelec":[1, 2, 5, 9]}
                                    """)
                    )
            )
            @Validated(Update.class)@RequestBody ReservationDTO dto){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.modify(dto));
    }

    @PutMapping("/confirm/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @Operation(
            summary = "El duenio Confirma una Reserva.",
            description = "Busca la ID de una reserva y le cambia su Estado a CONFIRM."
    )
    public ResponseEntity<Reservation> confirmReservation(@Parameter(description = "ID de la Reservación") @PathVariable Integer id){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.confirmReservation(id));
    }

    // Caso de uso: El Dueño rechaza la solicitud de reserva
    @PutMapping("/reject/{id}")
    @Operation(
            summary = "El duenio rechaza una Reserva.",
            description = "Busca la ID de una reserva y le cambia su Estado a REJECTED."
    )
    public ResponseEntity<Reservation> rejectReservation(@Parameter(description = "ID de la Reservación") @PathVariable Integer id){
        return ResponseEntity.ok(reservationService.rejectReservation(id));
    }

    @PutMapping("/complete/{id}")
    @Operation(
            summary = "Completa una Reserva.",
            description = "Busca la ID de una reserva y le cambia su Estado a COMPLETED."
    )
    public ResponseEntity<Reservation> completeReservation(@Parameter(description = "ID de la Reservación") @PathVariable Integer id){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.completeReservation(id));
    }

    @PutMapping("/cancel/{id}")
    @Operation(
            summary = "Cancela una Reserva.",
            description = "Busca la ID de una reserva y le cambia su Estado a CANCELLED."
    )
    public ResponseEntity<Reservation> cancelReservation(@Parameter(description = "ID de la Reservación") @PathVariable Integer id){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.cancelReservation(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Hace un borrado suave de una Reserva.",
            description = "Busca una Reserva por su ID y la marca como borrada(SoftDelete)."
    )
    public ResponseEntity <Reservation> softDelete (@Parameter(description = "ID de la Reservación") @PathVariable Integer id){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.softDelete(id));
    }
}
