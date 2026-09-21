package com.utn.space.venueaapi.controllers;

import com.utn.space.venueaapi.model.flags.Create;
import com.utn.space.venueaapi.model.flags.Update;
import com.utn.space.venueaapi.model.records.SpaceDTO;
import com.utn.space.venueaapi.model.records.SpaceServiceItemDTO;
import com.utn.space.venueaapi.service.SpaceServiceItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/services")
@Tag(name = "Servicios de los Espacios", description = "Operaciones sobre Servicios.")

public class SpaceServiceItemController {

    @Autowired
    private SpaceServiceItemService service;

    //Con este metodo listamos los servicios de un espacio para que un consumer pueda seleccionar los que guste
    @GetMapping("/space/{idSpace}")
    @Operation(
            summary = "Busca todos los Servicios de un Espacioi."
    )
    public ResponseEntity<List<SpaceServiceItemDTO>> listServicesFromSpace(
            @PathVariable Integer idSpace,
            Authentication authentication){
        if (authentication.getAuthorities().stream().anyMatch(r -> Objects.equals(r.getAuthority(), "ROLE_ADMIN"))){
            //Logica si es un Admin
            return ResponseEntity.ok(service.listOfServicesFromSpace(idSpace));
        }

        //Logica si no es Admin y no es duenio del spacio, oculta algunos datos
        return ResponseEntity.ok(service.ConsumerlistOfServicesFromSpace(idSpace));
    }

    //Este es el metodo que un Owner usaria para insertar servicios en uno de sus Espacios
    @PostMapping("/insert")
    @Operation(
            summary = "Crear un Servicio."
    )
    public ResponseEntity<Void> insertServiceItem(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Entra los datos obligatorios de la creación de un nuevo Servicio",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = SpaceServiceItemDTO.class),
                            examples = @ExampleObject(
                                    name = "Ejemplo",
                                    value = """
                                    {
                                      "id": null,
                                      "description":"Globos Inflables",
                                      "price":75000.00,
                                      "isActive":true,
                                      "idSpace":48
                                    """)
                    )
            )
            @Validated(Create.class) @RequestBody SpaceServiceItemDTO serviceItemDTO,
            Authentication authentication){
        if (authentication.getAuthorities().stream().anyMatch(r -> Objects.equals(r.getAuthority(), "ROLE_ADMIN"))){
            //Logica si es un Admin
            service.insertServiceItem(serviceItemDTO);
        }else {
            //Logica si no es Admin
            service.insertServiceItemOwner(serviceItemDTO);
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/update/{id}")
    @Operation(
            summary = "Actualiza un Servicio."
    )
    public ResponseEntity<Void> updateServiceItem(
            @PathVariable Integer id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Entra los datos obligatorios de la modificacion de un nuevo Servicio",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = SpaceServiceItemDTO.class),
                            examples = @ExampleObject(
                                    name = "Ejemplo",
                                    value = """
                                    {
                                      "id": 25,
                                      "description":"Globos Inflables",
                                      "price":75000.00,
                                      "isActive":true,
                                      "idSpace":48
                                    """)
                    )
            )
            @Validated(Update.class) @RequestBody SpaceServiceItemDTO serviceItemDTO,
            Authentication authentication){
        if (authentication.getAuthorities().stream().anyMatch(r -> Objects.equals(r.getAuthority(), "ROLE_ADMIN"))){
            //Logica si es un Admin
            service.updateServiceItem(id, serviceItemDTO);
        }else {
            //Logica si no es Admin
            service.updateServiceItemOwner(id,serviceItemDTO);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/space/{idSpace}/delete/{id}")
    @Operation(
            summary = "Elimina un Servicio."
    )
    public ResponseEntity<Void> deleteServiceFromSpace(
            @PathVariable Integer id,
            @PathVariable Integer idSpace,        //Revisar
            Authentication authentication){
        if (authentication.getAuthorities().stream().anyMatch(r -> Objects.equals(r.getAuthority(), "ROLE_ADMIN"))){
            //Logica si es un Admin
            System.out.println("DEBUG: Ejecutando lógica ADMIN");
            service.deleteServiceItem(id, idSpace);
        }else {
            //Logica si no es Admin
            System.out.println("DEBUG: Ejecutando lógica DUEÑO (no admin)");
            service.deleteServiceItemOwner(id,idSpace);
        }
        return ResponseEntity.ok().build();
    }
}
