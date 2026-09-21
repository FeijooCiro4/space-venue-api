package com.utn.space.venueaapi.controllers;

import com.utn.space.venueaapi.model.flags.Create;
import com.utn.space.venueaapi.model.flags.Update;
import com.utn.space.venueaapi.model.records.SpaceImageDTO;
import com.utn.space.venueaapi.model.SpaceImage;
import com.utn.space.venueaapi.service.SpaceImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spaceimages")
@Tag(name = "Imágenes", description = "Operaciones sobre Imágenes de Espacios.")

public class SpaceImageController {
    @Autowired
    SpaceImageService spaceImagesService;

    @GetMapping
    @Operation(
            summary = "Busca TODAS las Imágenes."
    )
    public ResponseEntity<List<SpaceImage>> listSpaceImages(){
        return ResponseEntity.ok(spaceImagesService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Busca una Imagen por su ID."
    )
    public ResponseEntity<SpaceImage> findSpaceImageById(@PathVariable Integer id){
        return ResponseEntity.ok(spaceImagesService.findById(id));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Borrar una Imagen."
    )
    public void deleteSapceImageById(@PathVariable Integer id){
        spaceImagesService.deleteById(id);
    }

    @PostMapping()
    @Operation(
            summary = "Crea una Imagen."
    )
    public void insertSpace(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Entra los datos obligatorios de la creación de una nueva Imagen",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = SpaceImageDTO.class),
                            examples = @ExampleObject(
                                    name = "Ejemplo",
                                    value = """
                                    {
                                      "idImage": null,
                                      "idSpace":74,
                                      "fileName":"Cocina.jpg",
                                      "urlImage":,
                                      "dateSend":"2026-09-15T21:00:00"}
                                    """)
                    )
            )
            @Validated(Create.class)@RequestBody SpaceImageDTO spaceImageDTO){
        spaceImagesService.insertSpaceImage(spaceImageDTO);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Modifica una Imagen."
    )
    public void modifySpaceImage(@PathVariable Integer id,
                                 @io.swagger.v3.oas.annotations.parameters.RequestBody(
                                         description = "Entra los datos obligatorios de la modificación de una Imagen",
                                         required = true,
                                         content = @Content(
                                                 schema = @Schema(
                                                         implementation = SpaceImageDTO.class),
                                                 examples = @ExampleObject(
                                                         name = "Ejemplo",
                                                         value = """
                                    {
                                      "idImage": null,
                                      "idSpace":74,
                                      "fileName":"Cocina.jpg",
                                      "urlImage":,
                                      "dateSend":"2026-09-15T21:00:00"}
                                    """)
                                         )
                                 )
                                 @Validated(Update.class)@RequestBody SpaceImageDTO spaceImageDTO){
        spaceImagesService.modifySpaceImage(id,spaceImageDTO);
    }

    @GetMapping("/byspaceid/{id}")
    @Operation(
            summary = "Busca TODAS las Imágenes de un Espacio."
    )
    public ResponseEntity<List<SpaceImage>> findAllBySpaceId(@PathVariable Integer id){
        return ResponseEntity.ok(spaceImagesService.findAllBySpaceId(id));
    }
}
