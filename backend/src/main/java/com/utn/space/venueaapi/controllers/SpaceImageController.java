package com.utn.space.venueaapi.controllers;

import com.utn.space.venueaapi.model.flags.Create;
import com.utn.space.venueaapi.model.flags.Update;
import com.utn.space.venueaapi.model.records.*;
import com.utn.space.venueaapi.service.mappers.PublicSpaceMapper;
import com.utn.space.venueaapi.service.ImageStorageService;
import com.utn.space.venueaapi.service.SpaceImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spaceimages")
@Tag(name = "Imágenes", description = "Operaciones sobre Imágenes de Espacios.")

public class SpaceImageController {
    @Autowired
    SpaceImageService spaceImagesService;
    @Autowired
    ImageStorageService imageStorageService;

    @GetMapping
    @Operation(
            summary = "Lista las imágenes de espacios",
            description = "Devuelve la lista de imágenes en el formato público SpaceImageResponseDTO."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Imágenes",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = SpaceImageResponseDTO.class)
            ))
    )
    public ResponseEntity<List<SpaceImageResponseDTO>> listSpaceImages(){
        return ResponseEntity.ok(spaceImagesService.findAll().stream().map(PublicSpaceMapper::toDto).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca una imagen por ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Imagen encontrada",
                    content = @Content(schema = @Schema(implementation = SpaceImageResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "No se encontró la imagen",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<SpaceImageResponseDTO> findSpaceImageById(@PathVariable Integer id){
        return ResponseEntity.ok(PublicSpaceMapper.toDto(spaceImagesService.findById(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('OFFERER') and @securityUtils.isSpaceImageOwner(#id, authentication.name))")
    @Operation(
            summary = "Borrar una Imagen."
    )
    @SecurityRequirement(name = "bearerAuth")
    public void deleteSapceImageById(@PathVariable Integer id){
        spaceImagesService.deleteById(id);
    }

    @PostMapping("/upload-url")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('OFFERER') and @securityUtils.isSpaceOwner(#request.idSpace(), authentication.name))")
    @Operation(
            summary = "Genera una URL prefirmada para subir una imagen a S3",
            description = """
                Admite JPG, PNG y WEBP de hasta 5 MiB. La URL vence a los 5 minutos.
                El cliente debe subir el archivo mediante PUT a uploadUrl usando el mismo
                Content-Type enviado en la solicitud y luego crear la imagen usando key
                como valor de urlImage.
                """
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ImageUploadUrlResponseDTO> createUploadUrl(@Valid @RequestBody ImageUploadUrlRequestDTO request){
        return ResponseEntity.ok(imageStorageService.createUploadUrl(request));
    }

    @PostMapping()
    @PreAuthorize("hasRole('ADMIN') or (hasRole('OFFERER') and @securityUtils.isSpaceOwner(#spaceImageDTO.idSpace(), authentication.name))")
    @Operation(
            summary = "Crea una Imagen.",
            description = "Crea una nueva Imagen para un Espacio. Se debe subir la imagen a S3 usando la URL prefirmada antes de crear la Imagen."
    )
    @ApiResponse(responseCode = "200", description = "Operación completada; respuesta sin cuerpo")
    @SecurityRequirement(name = "bearerAuth")
    public void insertSpace(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Entra los datos obligatorios de la creación de una nueva Imagen",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = SpaceImageDTO.class),
                            examples = @ExampleObject(
                                    name = "Crear imagen",
                                    value = """
                                    {
                                      "idImage": null,
                                      "idSpace": 74,
                                      "fileName": "Cocina.jpg",
                                      "urlImage": "media/spaces/74/3f2b6c1e-8a4d-4c1b-9e7a-2d5f0b9c1a3e.jpg",
                                      "dateSend": null
                                    }
                                    """
                            )
                    )
            )
            @Validated(Create.class)@RequestBody SpaceImageDTO spaceImageDTO){
        spaceImagesService.insertSpaceImage(spaceImageDTO);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasRole('OFFERER') and @securityUtils.isSpaceImageOwner(#id, authentication.name) and @securityUtils.isSpaceOwner(#spaceImageDTO.idSpace(), authentication.name))")
    @Operation(
            summary = "Modifica una Imagen.",
            description = "Modifica una Imagen existente. Se debe subir la imagen a S3 usando la URL prefirmada antes de modificar la Imagen."
    )
    @ApiResponse(responseCode = "200", description = "Operación completada; respuesta sin cuerpo")
    @SecurityRequirement(name = "bearerAuth")
    public void modifySpaceImage(@PathVariable Integer id,
                                 @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                         description = "Entra los datos obligatorios de la modificación de una Imagen",
                                         required = true,
                                         content = @Content(
                                                 schema = @Schema(
                                                         implementation = SpaceImageDTO.class),
                                                 examples = @ExampleObject(
                                                         name = "Actualizar imagen",
                                                         value = """
                                    {
                                      "idImage": 12,
                                      "idSpace": 74,
                                      "fileName": "Cocina.jpg",
                                      "urlImage": "media/spaces/74/3f2b6c1e-8a4d-4c1b-9e7a-2d5f0b9c1a3e.jpg",
                                      "dateSend": "2026-09-15T21:00:00"
                                    }
                                    """
                                                 )
                                         )
                                 )
                                 @Validated(Update.class)@RequestBody SpaceImageDTO spaceImageDTO){
        spaceImagesService.modifySpaceImage(id,spaceImageDTO);
    }

    @GetMapping("/byspaceid/{id}")
    @Operation(
            summary = "Busca TODAS las Imágenes de un Espacio."
    )
    public ResponseEntity<List<SpaceImageResponseDTO>> findAllBySpaceId(@PathVariable Integer id){
        return ResponseEntity.ok(spaceImagesService.findAllBySpaceId(id).stream().map(PublicSpaceMapper::toDto).toList());
    }
}
