package com.utn.space.venueaapi.controllers;

import com.utn.space.venueaapi.model.records.SelectServiceDTO;
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
            summary = "Busca TODAS las Reservas.",
            description = "Devuelve una lista Completa de Reservas."
    )
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
    public void selectListOfServicesOnReservation(
            @PathVariable Integer idReservation,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "IDs del catálogo. Precio y descripción se obtienen en el servidor.",
                    content = @Content(examples = @ExampleObject(value = "[{\"idService\":1},{\"idService\":2}]")))
            @Valid @RequestBody List<@Valid SelectServiceDTO> servicesSelectedDTO){
        service.insertListOfServicesSelectedInAReservation(idReservation, servicesSelectedDTO);
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityUtils.isSelectedServiceConsumer(#id, authentication.name)")
    @Operation(
            summary = "Elimina un Servicio Seleccionado.",
            description = "Elimina un Servicio Seleccionado por ID."
    )
    public void deselectOneServiceForAReservation(@PathVariable Integer id){
        service.deleteServiceSelectedForAReservation(id);
    }
}
