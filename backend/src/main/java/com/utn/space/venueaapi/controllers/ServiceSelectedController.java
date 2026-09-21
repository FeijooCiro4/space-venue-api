package com.utn.space.venueaapi.controllers;

import com.utn.space.venueaapi.model.records.ReservationDTO;
import com.utn.space.venueaapi.model.records.ServiceSelectedDTO;
import com.utn.space.venueaapi.service.ServiceSelectedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servicesselected")
@Tag(name = "Reservaciones", description = "Operaciones sobre Reservación.")
public class ServiceSelectedController {

    @Autowired
    private ServiceSelectedService service;

    @GetMapping("/reservation/{idReservation}")
    @Operation(
            summary = "Busca TODAS las Reservas.",
            description = "Devuelve una lista Completa de Reservas."
    )
    public ResponseEntity<List<ServiceSelectedDTO>> getServicesSelected(@PathVariable Integer idReservation){
        return ResponseEntity.ok(service.getServicesSelectedOfReservation(idReservation));
    }



    //Este es un metodo que usaría un Consumer para seleccionar los servicios de un espacio que quiere para una reserva
    @PostMapping("/insert/list/{idReservation}")
    @Operation(
            summary = "Agrega un Servicio Seleccionado a una reserva.",
            description = "Agrega un Servicio Seleccionado a una reserva por su ID."
    )
    public void selectListOfServicesOnReservation(
            @PathVariable Integer idReservation,
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
                                      "title":"Cumpleaños",
                                      "description":"Cumpleaños de Cecilia",
                                      "descriptionFrozen":"Globos inflables",
                                      "idReservation":1,
                                      "id_space":2}
                                    """)
                    )
            )
            @RequestBody List<ServiceSelectedDTO> servicesSelectedDTO){
        service.insertListOfServicesSelectedInAReservation(idReservation, servicesSelectedDTO);
    }

    @DeleteMapping("/delete/{id}")
    @Operation(
            summary = "Elimina un Servicio Seleccionado.",
            description = "Elimina un Servicio Seleccionado por ID."
    )
    public void deselectOneServiceForAReservation(@PathVariable Integer id){
        service.deleteServiceSelectedForAReservation(id);
    }
}
