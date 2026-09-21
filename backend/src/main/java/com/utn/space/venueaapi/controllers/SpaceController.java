package com.utn.space.venueaapi.controllers;

import com.utn.space.venueaapi.model.flags.Create;
import com.utn.space.venueaapi.model.flags.CreateAdmin;
import com.utn.space.venueaapi.model.flags.Update;
import com.utn.space.venueaapi.model.flags.UpdateAdmin;
import com.utn.space.venueaapi.model.records.SpaceDTO;
import com.utn.space.venueaapi.model.Space;
import com.utn.space.venueaapi.model.records.SpaceFilterDTO;
import com.utn.space.venueaapi.service.SpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spaces")
@Tag(name = "Espacios", description = "Operaciones sobre Espacios.")

public class SpaceController {
    @Autowired
    SpaceService spaceService;

    //Este listado incluye espacios inactivos
    @GetMapping("/showinactives")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Busca TODOS los Espacios, incluidos los inactivos."
    )
    public ResponseEntity<List<Space>> listSpaces(){
        return ResponseEntity.ok(spaceService.findAll());
    }

    //Este listado no incluye espacios inactivos
    @GetMapping()
    @Operation(
            summary = "Busca TODOS los Espacios disponibles."
    )
    public ResponseEntity<List<Space>> listActiveSpaces(){
        return ResponseEntity.ok(spaceService.findAllActives());
    }

    //Muestro todos los espacios de un owner, incluyendo inactivos
    @GetMapping("/ownedspaces")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @Operation(
            summary = "Busca TODOS los Espacios del Usuario."
    )
    public ResponseEntity<List<Space>> findAllForOwner(){
        return ResponseEntity.ok(spaceService.findAllForOwner());
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Busca un Espacio por ID."
    )
    public ResponseEntity<Space> findSpaceById(@PathVariable Integer id){
        return ResponseEntity.ok(spaceService.findById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Borra cualquier Espacio por su ID."
    )
    public void deleteAnySpaceById(@PathVariable Integer id){
        spaceService.deleteById(id);
    }

    @PostMapping()
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> insertSpace(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Entra los datos obligatorios de la creación de un nuevo Espacio",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = SpaceDTO.class),
                            examples = @ExampleObject(
                                    name = "Ejemplo",
                                    value = """
                                    {
                                      "id": null,
                                      "idConsumerOwner": 4,
                                      "location": ,
                                      "cancellationPolicies": ,
                                      "nameSpace":"Salon de Fiestas: Ejemplo",
                                      "description":"Salon de fiesta de 300 metros cuadrados",
                                      "basePrice": 25000.00,
                                      "publicationDate":"2026-05-20T011:25:31",
                                      "bufferTime":1,
                                      "active":true,
                                      "services":null}
                                    """)
                    )
            )
            @Validated(CreateAdmin.class)@RequestBody SpaceDTO spaceDTO){
        spaceService.insertSpace(spaceDTO);
        return ResponseEntity.ok("Espacio creado exitosamente");
    }

    //Lo que hace @Validated es no permitir que se mande un DTO incompleto
    @PostMapping("/ownedspace")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @Operation(
            summary = "Crea un nuevo Espacio."
    )
    public ResponseEntity<Object> insertOwnedSpace(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Entra los datos obligatorios de la creación de un nuevo Espacio",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = SpaceDTO.class),
                            examples = @ExampleObject(
                                    name = "Ejemplo",
                                    value = """
                                    {
                                      "id": null,
                                      "idConsumerOwner": null,
                                      "location": ,
                                      "cancellationPolicies": ,
                                      "nameSpace":"Salon de Fiestas: Ejemplo",
                                      "description":"Salon de fiesta de 300 metros cuadrados",
                                      "basePrice": 25000.00,
                                      "publicationDate":"2026-05-20T011:25:31",
                                      "bufferTime":1,
                                      "active":true,
                                      "services":null}
                                    """)
                    )
            )
            @Validated(Create.class)@RequestBody SpaceDTO spaceDTO){
        var created = spaceService.insertOwnedSpace(spaceDTO);
        // Retornamos el ID para asociar imágenes al espacio creado
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(java.util.Map.of("idSpace", created.getIdSpace()));
    }

    @PutMapping("/ownedspace/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @Operation(
            summary = "Modifica un Espacio."
    )
    public ResponseEntity<String> modifyOwnedSpace(@PathVariable Integer id,
                                 @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                         description = "Entra los datos obligatorios de la creación de un nuevo Espacio",
                                         required = true,
                                         content = @Content(
                                                 schema = @Schema(
                                                         implementation = SpaceDTO.class),
                                                 examples = @ExampleObject(
                                                         name = "Ejemplo",
                                                         value = """
                                    {
                                      "id": 85,
                                      "idConsumerOwner": null,
                                      "location": ,
                                      "cancellationPolicies": ,
                                      "nameSpace":"Salon de Fiestas: Ejemplo",
                                      "description":"Salon de fiesta de 300 metros cuadrados",
                                      "basePrice": 25000.00,
                                      "publicationDate":"2026-05-20T011:25:31",
                                      "bufferTime":1,
                                      "active":true,
                                      "services":null}
                                    """)
                                         )
                                 )
                                 @Validated(Update.class)@RequestBody SpaceDTO spaceDTO){
        spaceService.modifyOwnedSpace(id,spaceDTO);
        return ResponseEntity.ok("Espacio actualizado exitosamente");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Modifica un Espacio como Admin"
    )
    public ResponseEntity<String> modifySpace(@PathVariable Integer id,
                            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                    description = "Entra los datos obligatorios de la creación de un nuevo Espacio",
                                    required = true,
                                    content = @Content(
                                            schema = @Schema(
                                                    implementation = SpaceDTO.class),
                                            examples = @ExampleObject(
                                                    name = "Ejemplo",
                                                    value = """
                                    {
                                      "id": null,
                                      "idConsumerOwner": 4,
                                      "location": ,
                                      "cancellationPolicies": ,
                                      "nameSpace":"Salon de Fiestas: Ejemplo",
                                      "description":"Salon de fiesta de 300 metros cuadrados",
                                      "basePrice": 25000.00,
                                      "publicationDate":"2026-05-20T011:25:31",
                                      "bufferTime":1,
                                      "active":true,
                                      "services":null}
                                    """)
                                    )
                            )
                            @Validated(UpdateAdmin.class)@RequestBody SpaceDTO spaceDTO){
        spaceService.modifySpace(id,spaceDTO);
        return ResponseEntity.ok("Espacio actualizado exitosamente");
    }

    @PostMapping("/byfields")
    @Operation(
            summary = "Busca TODOS los Espacios Filtrados.",
            description = "Devuelve una lista Completa de Espacios Disponibles."
    )
    public ResponseEntity<List<Space>> findAllByFields(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Entra los datos obligatorios de la creación de un nuevo Espacio",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = SpaceDTO.class),
                            examples = @ExampleObject(
                                    name = "Ejemplo",
                                    value = """
                                    {
                                      "id": null,
                                      "idConsumerOwner": 4,
                                      "location": ,
                                      "cancellationPolicies": ,
                                      "nameSpace":"Salon de Fiestas: Ejemplo",
                                      "description":"Salon de fiesta de 300 metros cuadrados",
                                      "basePrice": 25000.00,
                                      "publicationDate":"2026-05-20T011:25:31",
                                      "bufferTime":1,
                                      "active":true,
                                      "services":null}
                                    """)
                    )
            )
            @Validated(Update.class)@RequestBody SpaceFilterDTO spaceFilterDTO){
        return ResponseEntity.ok(spaceService.findAllByFields(spaceFilterDTO));
    }

    //Este metodo si va a mostrar los espacios no disponibles
    @RequestMapping(value = "/byfields/showinactives", method = {RequestMethod.GET, RequestMethod.POST})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Busca TODOS los Espacios Filtrados.",
            description = "Busca TODOS los Espacios Filtrados incluido los inactivos."
    )
    public ResponseEntity<List<Space>> findAllByFieldsWithInactives(@RequestBody SpaceFilterDTO spaceFilterDTO){
        return ResponseEntity.ok(spaceService.findAllByFieldsWithInactives(spaceFilterDTO));
    }

    @RequestMapping(value = "/ownedspaces/byfields", method = {RequestMethod.GET, RequestMethod.POST})
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @Operation(
            summary = "Busca TODOS los Espacios filtrados de un dueño."
    )
    public ResponseEntity<List<Space>> findAllOwnedSpacesbyFields(@RequestBody SpaceFilterDTO spaceFilterDTO){
        return ResponseEntity.ok(spaceService.findAllByFieldsForOwner(spaceFilterDTO));
    }

    @DeleteMapping("/ownedspace/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    @Operation(
            summary = "Borra un Espacio propio.",
            description = "Borra un Espacio propio por su ID."
    )
    public ResponseEntity<String> deleteOwnedSpace(@PathVariable Integer id){
        spaceService.deleteOwnedSpace(id);
        return ResponseEntity.ok("Espacio eliminado con exito");
    }

}
