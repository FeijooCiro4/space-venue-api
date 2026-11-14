package com.utn.space.venueaapi.controllers;

import com.utn.space.venueaapi.model.records.SelectServiceDTO;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import com.utn.space.venueaapi.model.records.ServiceSelectedDTO;
import com.utn.space.venueaapi.service.ServiceSelectedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servicesselected")
@Tag(name = "Reservaciones", description = "Operaciones sobre Reservación.")
public class ServiceSelectedController {

    @Autowired
    private ServiceSelectedService service;

    @GetMapping("/reservation/{idReservation}")
    @PreAuthorize("hasRole('ADMIN') or @securityUtils.canReadReservation(#idReservation, authentication.name)")
    @Operation(
            summary = "Lista los servicios seleccionados de una reserva",
            description = "Devuelve el catálogo contratado para la reserva indicada. Solo puede consultarla un administrador o quien tenga permiso para leer esa reserva."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Servicios seleccionados",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = ServiceSelectedDTO.class)
            ))
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<ServiceSelectedDTO>> getServicesSelected(@PathVariable Integer idReservation){
        return ResponseEntity.ok(service.getServicesSelectedOfReservation(idReservation));
    }



    //Este es un metodo que usaría un Consumer para seleccionar los servicios de un espacio que quiere para una reserva
    @PostMapping("/insert/list/{idReservation}")
    @PreAuthorize("hasRole('ADMIN') or @securityUtils.isConsumerOfReservation(#idReservation, authentication.name)")
    @Operation(
            summary = "Agrega un Servicio Seleccionado a una reserva.",
            description = "Agrega un Servicio Seleccionado a una reserva por su ID."
    )
    @ApiResponse(responseCode = "200", description = "Servicios agregados; respuesta sin cuerpo")
    @SecurityRequirement(name = "bearerAuth")
    public void selectListOfServicesOnReservation(
            @PathVariable Integer idReservation,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = """
                Lista de IDs de servicios del catálogo del espacio reservado.
                Los IDs deben ser positivos, únicos y corresponder a servicios activos.
                La reserva debe estar en estado TENTATIVE y no tener pagos registrados.
                """,
                    required = true,
                    content = @Content(
                            array = @ArraySchema(schema = @Schema(type = "integer", format = "int32")),
                            examples = @ExampleObject(
                                    name = "Servicios seleccionados",
                                    value = "[1, 2]"
                            )
                    )
            )
            @Valid @RequestBody List<@Valid Integer> servicesSelectedDTO){
        service.insertListOfServicesSelectedInAReservation(idReservation, servicesSelectedDTO);
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityUtils.isSelectedServiceConsumer(#id, authentication.name)")
    @Operation(
            summary = "Quita un servicio seleccionado de una reserva",
            description = "Solo se permite mientras la reserva esté TENTATIVE y no tenga pagos registrados."
    )
    @ApiResponse(responseCode = "200", description = "Servicio quitado; respuesta sin cuerpo")
    @SecurityRequirement(name = "bearerAuth")
    public void deselectOneServiceForAReservation(@PathVariable Integer id){
        service.deleteServiceSelectedForAReservation(id);
    }
}
