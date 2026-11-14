package com.utn.space.venueaapi.controllers;

import com.utn.space.venueaapi.model.flags.Create;
import com.utn.space.venueaapi.model.flags.CreateAdmin;
import com.utn.space.venueaapi.model.flags.Update;
import com.utn.space.venueaapi.model.flags.UpdateAdmin;
import com.utn.space.venueaapi.model.records.*;
import com.utn.space.venueaapi.service.mappers.PublicSpaceMapper;
import com.utn.space.venueaapi.service.SpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<SpaceResponseDTO>> listSpaces(){
        return ResponseEntity.ok(spaceService.findAll().stream().map(PublicSpaceMapper::toDto).toList());
    }

    //Este listado no incluye espacios inactivos
    @GetMapping()
    @Operation(
            summary = "Busca TODOS los Espacios disponibles."
    )
    public ResponseEntity<List<SpaceResponseDTO>> listActiveSpaces(){
        return ResponseEntity.ok(spaceService.findAllActives().stream().map(PublicSpaceMapper::toDto).toList());
    }

    //Muestro todos los espacios de un owner, incluyendo inactivos
    @GetMapping("/ownedspaces")
    @PreAuthorize("hasAnyRole('OFFERER', 'ADMIN')")
    @Operation(
            summary = "Busca TODOS los Espacios del Usuario."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<SpaceResponseDTO>> findAllForOwner(){
        return ResponseEntity.ok(spaceService.findAllForOwner().stream().map(PublicSpaceMapper::toDto).toList());
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Busca un Espacio por ID."
    )
    public ResponseEntity<SpaceResponseDTO> findSpaceById(@PathVariable Integer id){
        return ResponseEntity.ok(PublicSpaceMapper.toDto(spaceService.findById(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Desactiva un espacio",
            description = "Realiza una baja lógica: marca el espacio como inactivo y registra que fue desactivado por un administrador. No elimina físicamente el registro."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Espacio desactivado; respuesta sin cuerpo"
    )
    @SecurityRequirement(name = "bearerAuth")
    public void deleteAnySpaceById(@PathVariable Integer id){
        spaceService.deleteById(id);
    }

    @PutMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Activa o desactiva cualquier Espacio por su ID."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MessageResponseDTO> setSpaceActive(@PathVariable Integer id, @RequestParam Boolean active){
        spaceService.setActive(id, active);
        return ResponseEntity.ok(new MessageResponseDTO(active ? "Espacio activado." : "Espacio desactivado."));
    }

    @PostMapping()
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Crea un nuevo Espacio como Admin."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Espacio creado",
                    content = @Content(schema = @Schema(implementation = MessageResponseDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
    public ResponseEntity<MessageResponseDTO> insertSpace(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Entra los datos obligatorios de la creación de un nuevo Espacio",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = SpaceDTO.class),
                            examples = @ExampleObject(
                                    name = "Crear espacio como administrador",
                                    value = """
                                    {
                                      "idSpace": null,
                                      "idConsumerOwner": 4,
                                      "location": {
                                        "latitude": -34.6037,
                                        "longitude": -58.3816
                                      },
                                      "cancellationPolicies": "FLEXIBLE",
                                      "nameSpace": "Salón de fiestas",
                                      "description": "Salón para eventos de hasta 100 personas",
                                      "basePrice": 25000.00,
                                      "publicationDate": "2026-05-20",
                                      "bufferTime": 60,
                                      "active": true,
                                      "services": []
                                    }
                                    """
                            )
                    )
            )
            @Validated(CreateAdmin.class)@RequestBody SpaceDTO spaceDTO){
        spaceService.insertSpace(spaceDTO);
        return ResponseEntity.ok(new  MessageResponseDTO("Espacio creado exitosamente."));
    }

    //Lo que hace @Validated es no permitir que se mande un DTO incompleto
    @PostMapping("/ownedspace")
    @PreAuthorize("hasAnyRole('OFFERER', 'ADMIN')")
    @Operation(
            summary = "Crea un espacio propio",
            description = "El dueño se obtiene del JWT. El espacio queda pendiente de aprobación y se devuelve su ID para asociar imágenes."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Espacio creado",
                    content = @Content(
                            schema = @Schema(
                                    type = "object",
                                    example = "{\"idSpace\":85}"
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))
            )
    })
    public ResponseEntity<Object> insertOwnedSpace(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Entra los datos obligatorios de la creación de un nuevo Espacio",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = SpaceDTO.class),
                            examples = @ExampleObject(
                                    name = "Crear espacio propio",
                                    value = """
                                    {
                                      "idSpace": null,
                                      "idConsumerOwner": null,
                                      "location": {
                                        "latitude": -34.6037,
                                        "longitude": -58.3816
                                      },
                                      "cancellationPolicies": "FLEXIBLE",
                                      "nameSpace": "Salón de fiestas",
                                      "description": "Salón para eventos de hasta 100 personas",
                                      "basePrice": 25000.00,
                                      "publicationDate": null,
                                      "bufferTime": 60,
                                      "active": null,
                                      "services": []
                                    }
                                    """
                            )
                    )
            )
            @Validated(Create.class)@RequestBody SpaceDTO spaceDTO){
        var created = spaceService.insertOwnedSpace(spaceDTO);
        // Retornamos el ID para asociar imágenes al espacio creado
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(java.util.Map.of("idSpace", created.getIdSpace()));
    }

    @PutMapping("/ownedspace/{id}")
    @PreAuthorize("hasAnyRole('OFFERER', 'ADMIN')")
    @Operation(
            summary = "Modifica un Espacio."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MessageResponseDTO> modifyOwnedSpace(@PathVariable Integer id,
                                 @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                         description = "Entra los datos obligatorios de la creación de un nuevo Espacio",
                                         required = true,
                                         content = @Content(
                                                 schema = @Schema(
                                                         implementation = SpaceDTO.class),
                                                 examples = @ExampleObject(
                                                         name = "Actualizar espacio propio",
                                                         value = """
                                                    {
                                                      "idSpace": 85,
                                                      "idConsumerOwner": null,
                                                      "location": {
                                                        "latitude": -34.6037,
                                                        "longitude": -58.3816
                                                      },
                                                      "cancellationPolicies": "MODERATED",
                                                      "nameSpace": "Salón actualizado",
                                                      "description": "Salón actualizado para eventos",
                                                      "basePrice": 30000.00,
                                                      "publicationDate": null,
                                                      "bufferTime": 60,
                                                      "active": null,
                                                      "services": null
                                                    }
                                                    """
                                                 )
                                         )
                                 )
                                 @Validated(Update.class)@RequestBody SpaceDTO spaceDTO){
        spaceService.modifyOwnedSpace(id,spaceDTO);
        return ResponseEntity.ok(new MessageResponseDTO("Espacio actualizado exitosamente."));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Modifica un Espacio como Admin"
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MessageResponseDTO> modifySpace(@PathVariable Integer id,
                            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                    description = "Entra los datos obligatorios de la creación de un nuevo Espacio",
                                    required = true,
                                    content = @Content(
                                            schema = @Schema(
                                                    implementation = SpaceDTO.class),
                                            examples = @ExampleObject(
                                                    name = "Actualizar espacio como administrador",
                                                    value = """
                                                    {
                                                      "idSpace": 85,
                                                      "idConsumerOwner": null,
                                                      "location": {
                                                        "latitude": -34.6037,
                                                        "longitude": -58.3816
                                                      },
                                                      "cancellationPolicies": "MODERATED",
                                                      "nameSpace": "Salón actualizado",
                                                      "description": "Salón actualizado para eventos",
                                                      "basePrice": 30000.00,
                                                      "publicationDate": null,
                                                      "bufferTime": 60,
                                                      "active": null,
                                                      "services": null
                                                    }
                                                    """
                                            )
                                    )
                            )
                            @Validated(UpdateAdmin.class)@RequestBody SpaceDTO spaceDTO){
        spaceService.modifySpace(id,spaceDTO);
        return ResponseEntity.ok(new MessageResponseDTO("Espacio actualizado exitosamente"));
    }

    @PostMapping("/byfields")
    @Operation(
            summary = "Busca TODOS los Espacios Filtrados.",
            description = "Devuelve una lista Completa de Espacios Disponibles."
    )
    public ResponseEntity<List<SpaceResponseDTO>> findAllByFields(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = """
                Filtros opcionales. Se pueden combinar: idConsumerOwner, idLocation,
                nameSpace, minPrice, maxPrice, lat, lng y radious.
                La búsqueda por proximidad se aplica cuando se envían lat y lng;
                si se omite radious, se usa un radio de 5 km.
                """,
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = SpaceFilterDTO.class),
                            examples = @ExampleObject(
                                    name = "Filtro por nombre, precio y proximidad",
                                    value = """
                                {
                                  "idConsumerOwner": null,
                                  "idLocation": null,
                                  "nameSpace": "Salón",
                                  "minPrice": 10000.0,
                                  "maxPrice": 50000.0,
                                  "lat": -34.6037,
                                  "lng": -58.3816,
                                  "radious": 5.0
                                }
                                """
                            )
                    )
            )
            @Validated(Update.class)@RequestBody SpaceFilterDTO spaceFilterDTO){
        return ResponseEntity.ok(spaceService.findAllByFields(spaceFilterDTO).stream().map(PublicSpaceMapper::toDto).toList());
    }

    //Este metodo si va a mostrar los espacios no disponibles
    @RequestMapping(value = "/byfields/showinactives", method = {RequestMethod.GET, RequestMethod.POST})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Busca TODOS los Espacios Filtrados. Funciona como GET Y POST para poder mandar el filtro en el body.",
            description = "Busca TODOS los Espacios Filtrados incluido los inactivos."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<SpaceResponseDTO>> findAllByFieldsWithInactives(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = """
                Filtros opcionales. Se pueden combinar: idConsumerOwner, idLocation,
                nameSpace, minPrice, maxPrice, lat, lng y radious.
                La búsqueda por proximidad se aplica cuando se envían lat y lng;
                si se omite radious, se usa un radio de 5 km.
                """,
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = SpaceFilterDTO.class),
                            examples = @ExampleObject(
                                    name = "Filtro por nombre, precio y proximidad",
                                    value = """
                                {
                                  "idConsumerOwner": null,
                                  "idLocation": null,
                                  "nameSpace": "Salón",
                                  "minPrice": 10000.0,
                                  "maxPrice": 50000.0,
                                  "lat": -34.6037,
                                  "lng": -58.3816,
                                  "radious": 5.0
                                }
                                """
                            )
                    )
            )
            @RequestBody SpaceFilterDTO spaceFilterDTO){
        return ResponseEntity.ok(spaceService.findAllByFieldsWithInactives(spaceFilterDTO).stream().map(PublicSpaceMapper::toDto).toList());
    }

    @RequestMapping(value = "/ownedspaces/byfields", method = {RequestMethod.GET, RequestMethod.POST})
    @PreAuthorize("hasAnyRole('OFFERER', 'ADMIN')")
    @Operation(
            summary = "Busca TODOS los Espacios filtrados de un dueño. Funciona como GET Y POST para poder mandar el filtro en el body."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<SpaceResponseDTO>> findAllOwnedSpacesbyFields(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = """
                Filtros opcionales. Se pueden combinar: idConsumerOwner, idLocation,
                nameSpace, minPrice, maxPrice, lat, lng y radious.
                La búsqueda por proximidad se aplica cuando se envían lat y lng;
                si se omite radious, se usa un radio de 5 km.
                """,
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = SpaceFilterDTO.class),
                            examples = @ExampleObject(
                                    name = "Filtro por nombre, precio y proximidad",
                                    value = """
                                {
                                  "idConsumerOwner": null,
                                  "idLocation": null,
                                  "nameSpace": "Salón",
                                  "minPrice": 10000.0,
                                  "maxPrice": 50000.0,
                                  "lat": -34.6037,
                                  "lng": -58.3816,
                                  "radious": 5.0
                                }
                                """
                            )
                    )
            )
            @RequestBody SpaceFilterDTO spaceFilterDTO){
        return ResponseEntity.ok(spaceService.findAllByFieldsForOwner(spaceFilterDTO).stream().map(PublicSpaceMapper::toDto).toList());
    }

    @DeleteMapping("/ownedspace/{id}")
    @PreAuthorize("hasAnyRole('OFFERER', 'ADMIN')")
    @Operation(
            summary = "Marca un espacio propio como eliminado",
            description = "Realiza una baja lógica solicitada por su dueño. El espacio queda inactivo y marcado como eliminado por el dueño."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Espacio eliminado; respuesta con mensaje",
            content = @Content(schema = @Schema(implementation = MessageResponseDTO.class))
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MessageResponseDTO> deleteOwnedSpace(@PathVariable Integer id){
        spaceService.deleteOwnedSpace(id);
        return ResponseEntity.ok(new MessageResponseDTO("Espacio eliminado con exito."));
    }

}
