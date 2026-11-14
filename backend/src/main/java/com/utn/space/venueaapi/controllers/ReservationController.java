package com.utn.space.venueaapi.controllers;

import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.utn.space.venueaapi.model.Reservation;
import com.utn.space.venueaapi.model.flags.Create;
import com.utn.space.venueaapi.model.flags.Update;
import com.utn.space.venueaapi.model.records.ErrorResponseDTO;
import com.utn.space.venueaapi.model.records.ReservationCalendarDTO;
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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
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
    @Operation(
            summary = "Genera el enlace de pago de una reserva",
            description = """
                La reserva debe pertenecer al usuario autenticado y estar en estado CONFIRMED.
                Devuelve la URL de inicio de pago de Mercado Pago.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Preferencia de pago creada",
                    content = @Content(
                            schema = @Schema(
                                    type = "object",
                                    example = "{\"initPoint\":\"https://www.mercadopago.com/checkout/...\"}"
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "La reserva no está confirmada",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "502", description = "Error al comunicarse con Mercado Pago",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "500", description = "Error inesperado",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @PreAuthorize("hasAnyRole('CLIENT', 'OFFERER') and @securityUtils.isReservationOwner(#id, authentication.name)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> createPaymentPreference(@Parameter(description = "ID de la Reservación") @PathVariable Integer id) {
        try {
            Reservation reservation = reservationService.findById(id);

            // Validar que la reserva esté confirmada por el dueño antes de poder pagar
            if (!"CONFIRMED".equals(reservation.getStatus().toString())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponseDTO(400, HttpStatus.BAD_REQUEST.getReasonPhrase(), "La reserva debe ser confirmada por el dueño del espacio antes de poder pagar."));
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
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponseDTO(502, HttpStatus.BAD_GATEWAY.getReasonPhrase(), "Error al comunicarse con la pasarela de pagos"));
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(this.getClass()).error("❌ Error inesperado en checkout para reserva {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponseDTO(500, HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "Error inesperado del sistema"));
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Busca TODAS las Reservas.",
            description = "Devuelve una lista Completa de Reservas."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<Reservation>> findAll (){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.findAll());
    }

    @GetMapping("/me")
    @Operation(
            summary = "Lista las reservas del usuario autenticado",
            description = "Devuelve las reservas asociadas al consumidor identificado por el JWT. Disponible para CLIENT, OFFERER y ADMIN."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos para esta operación",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "No se encontró la reserva",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "La transición no está permitida para el estado actual",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('CLIENT', 'OFFERER', 'ADMIN')")
    public ResponseEntity<List<Reservation>> findAllForCurrentConsumer() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.findAllForLoggedConsumer());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityUtils.canReadReservation(#id, authentication.name)")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Busca una Reserva.",
            description = "Busca la ID de una reserva y la devuelve."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "No autenticado",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos para esta operación",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "No se encontró la reserva",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "La transición no está permitida para el estado actual",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<Reservation> findById ( @Parameter(description = "ID de la Reservación") @PathVariable Integer id){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.findById(id));
    }

    @GetMapping("/space/{idSpace}")
    @PreAuthorize("hasRole('ADMIN') or @securityUtils.isSpaceOwner(#idSpace, authentication.name)")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Busca reservas de un espacio.",
            description = "En base al ID de un espacio, devuelve todas las reservas asociadas a ese espacio (siempre y cuando el usuario actual en sesión sea el dueño de ese espacio)."
    )
    public ResponseEntity<List<Reservation>> findAllBySpaceId(@Parameter(description = "ID del Espacio") @PathVariable Integer idSpace) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.findAllBySpaceId(idSpace));
    }

    @GetMapping("/space/{idSpace}/calendar")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Calendario de reservas de un espacio.",
            description = "Devuelve, en orden cronológico, las reservas pendientes o confirmadas de un espacio entre dos fechas (inclusive, máximo 62 días). "
                    + "Cualquier usuario logueado ve los horarios ocupados y su estado; el id, título y cliente solo se incluyen para el dueño del espacio, el admin o el cliente de esa reserva."
    )
    public ResponseEntity<List<ReservationCalendarDTO>> findCalendarBySpaceId(
            @Parameter(description = "ID del Espacio") @PathVariable Integer idSpace,
            @Parameter(description = "Primer día del rango (yyyy-MM-dd)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Último día del rango, inclusive (yyyy-MM-dd)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.findCalendarBySpaceId(idSpace, from, to, authentication.getName(), isAdmin));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'OFFERER', 'ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Crea una reserva",
            description = """
                El consumidor titular se obtiene del JWT. El servidor asigna el estado inicial
                TENTATIVE, calcula el precio y activa la reserva; esos valores no se toman del body.
                El espacio debe estar activo, las fechas deben ser futuras y estar disponibles,
                y no se puede reservar el espacio propio.
                """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Reserva creada",
                    content = @Content(schema = @Schema(implementation = Reservation.class))
            ),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o reserva no disponible",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Falta el Bearer token o es inválido")
    })
    public ResponseEntity<Reservation> createReservation(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Datos del evento, espacio y servicios opcionales. No incluir idConsumer para determinar el titular.",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = ReservationDTO.class),
                            examples = @ExampleObject(
                                    name = "Nueva reserva",
                                    value = """
                                {
                                  "id": null,
                                  "title": "Cumpleaños",
                                  "description": "Cumpleaños de Cecilia",
                                  "fromDate": "2027-09-15T21:00:00",
                                  "untilDate": "2027-09-16T05:00:00",
                                  "finalPrice": null,
                                  "status": null,
                                  "createdAt": null,
                                  "isActive": null,
                                  "idConsumer": null,
                                  "idSpace": 2,
                                  "idServicesSelec": [1, 2]
                                }
                                """
                            )
                    )
            )
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
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN') or (@securityUtils.isConsumerOfReservation(#dto.id(), authentication.name) and @securityUtils.isCurrentConsumer(#dto.idConsumer(), authentication.name))")
    @Operation(
            summary = "Modifica una reserva",
            description = """
                Modifica los datos de una reserva existente. Solo se puede editar si su estado
                es TENTATIVE y todavía no tiene un pago registrado. El idConsumer debe coincidir
                con el titular actual; el precio final se recalcula en el servidor.
                """
    )
    public ResponseEntity<Reservation> modifyReservation(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Datos actualizados de la reserva.",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = ReservationDTO.class),
                            examples = @ExampleObject(
                                    name = "Actualizar reserva",
                                    value = """
                                {
                                  "id": 4,
                                  "title": "Cumpleaños actualizado",
                                  "description": "Evento familiar",
                                  "fromDate": "2027-09-15T21:00:00",
                                  "untilDate": "2027-09-16T05:00:00",
                                  "idConsumer": 1,
                                  "idSpace": 2,
                                  "idServicesSelec": [1, 2]
                                }
                                """
                            )
                    )
            )
            @Validated(Update.class)@RequestBody ReservationDTO dto){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.modify(dto));
    }

    @PutMapping("/confirm/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN') or @securityUtils.isSpaceOwnerOfReservation(#id, authentication.name)")
    @Operation(
            summary = "El dueño confirma una reserva",
            description = "Cambia el estado de TENTATIVE a CONFIRMED. Solo se puede confirmar una reserva activa pendiente."
    )
    public ResponseEntity<Reservation> confirmReservation(@Parameter(description = "ID de la Reservación") @PathVariable Integer id){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.confirmReservation(id));
    }

    // Caso de uso: El Dueño rechaza la solicitud de reserva
    @PutMapping("/reject/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityUtils.isSpaceOwnerOfReservation(#id, authentication.name)")
    @Operation(
            summary = "El dueño rechaza una reserva",
            description = "Cambia el estado de una reserva activa de TENTATIVE a REJECTED."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Reservation> rejectReservation(@Parameter(description = "ID de la Reservación") @PathVariable Integer id){
        return ResponseEntity.ok(reservationService.rejectReservation(id));
    }

    @PutMapping("/complete/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityUtils.isSpaceOwnerOfReservation(#id, authentication.name)")
    @Operation(
            summary = "Completa una reserva",
            description = "Cambia el estado de una reserva activa de CONFIRMED a COMPLETED."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Reservation> completeReservation(@Parameter(description = "ID de la Reservación") @PathVariable Integer id){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.completeReservation(id));
    }

    @PutMapping("/cancel/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityUtils.canReadReservation(#id, authentication.name)")
    @Operation(
            summary = "Cancela una reserva",
            description = "Cambia el estado de TENTATIVE o CONFIRMED a CANCELLED."
    )
    @SecurityRequirement(name = "bearerAuth")
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
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity <Reservation> softDelete (@Parameter(description = "ID de la Reservación") @PathVariable Integer id){
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reservationService.softDelete(id));
    }
}
