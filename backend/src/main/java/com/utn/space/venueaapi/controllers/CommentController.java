package com.utn.space.venueaapi.controllers;

import com.utn.space.venueaapi.model.records.CommentDTO;
import com.utn.space.venueaapi.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/comments")
@Tag(name = "Comentarios", description = "Operaciones sobre Comentarios.")

public class CommentController {
    @Autowired
    CommentService commentService;

    @GetMapping
    @Operation(
            summary = "Busca TODOS los Comentarios.",
            description = "Devuelve una lista Completa de Comentarios."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<CommentDTO>> listAllComments(){
        return ResponseEntity.ok(commentService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Busca un Comentario.",
            description = "Busca un Comentario por su ID."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CommentDTO> findCommentById(@PathVariable Integer id){
        return ResponseEntity.ok(commentService.findById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'OFFERER', 'ADMIN')")
    @Operation(
            summary = "Elimina un Comentario.",
            description = "Elimina un Comentario por ID."
    )
    @SecurityRequirement(name = "bearerAuth")
    public void deleteCommentById(@PathVariable Integer id, Authentication authentication){
        //Las autoridades se obtienen de la autenticación del JWT
        if (authentication.getAuthorities().stream().anyMatch(r -> Objects.equals(r.getAuthority(), "ROLE_ADMIN"))){
            //Logica si es un Admin
            commentService.deleteById(id);
        }else {
            //Logica si es un client
            commentService.deleteByIdCustomer(id);
        }
    }

    @DeleteMapping("/deactivate/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'OFFERER', 'ADMIN')")
    @Operation(
            summary = "Elimina un Comentario.",
            description = "Elimina, en baja lógica, un Comentario por ID."
    )
    @SecurityRequirement(name = "bearerAuth")
    public void lowDeleteCommentById(@PathVariable Integer id, Authentication authentication){
        if (authentication.getAuthorities().stream().anyMatch(r -> Objects.equals(r.getAuthority(), "ROLE_ADMIN"))){
            commentService.adminDeletion(id);
        } else {
            commentService.consumerDeletion(id);
        }
    }

    @DeleteMapping("/activate/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    @Operation(
            summary = "Activar un Comentario.",
            description = "Activar un Comentario por ID."
    )
    @SecurityRequirement(name = "bearerAuth")
    public void activateCommentById(@PathVariable Integer id, Authentication authentication){
        commentService.adminActivation(id);
    }

    @PostMapping()
    @PreAuthorize("hasAnyRole('CLIENT', 'OFFERER', 'ADMIN')")
    @Operation(
            summary = "Comenta un espacio",
            description = """
                Un usuario autenticado puede comentar un espacio si tiene una reserva activa
                en estado CONFIRMED o COMPLETED. Para usuarios no administradores, la autoría
                se determina con la sesión, no con idConsumer o username del body.
                """
    )
    @SecurityRequirement(name = "bearerAuth")
    public void insertComment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Espacio, texto y puntuación del comentario. La puntuación va de 0 a 5.",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = CommentDTO.class),
                            examples = @ExampleObject(
                                    name = "Comentario",
                                    value = """
                                {
                                  "idComment": null,
                                  "idConsumer": null,
                                  "username": null,
                                  "idSpace": 44,
                                  "description": "Muy espacioso",
                                  "score": 5,
                                  "isActive": null,
                                  "deactivationReason": null,
                                  "created_at": null
                                }
                                """
                            )
                    )
            )
            @RequestBody CommentDTO commentDTO,
            Authentication authentication){
        if (authentication.getAuthorities().stream().anyMatch(r -> Objects.equals(r.getAuthority(), "ROLE_ADMIN"))){
            //Logica si es un Admin
            commentService.insertComment(commentDTO);
        }else {
            //Logica si es un client
            commentService.consumerInsertCommentOnSpace(commentDTO);
        }
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Modifica un comentario",
            description = "Actualiza el texto y la puntuación de un comentario existente. El usuario autenticado solo puede modificar comentarios propios; el administrador puede modificar cualquiera."
    )
    public void modifyComment(
            @PathVariable Integer id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Datos actualizados del comentario. La puntuación debe estar entre 0 y 5.",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = CommentDTO.class),
                            examples = @ExampleObject(
                                    name = "Actualizar comentario",
                                    value = """
                                {
                                  "idComment": 12,
                                  "idConsumer": null,
                                  "username": null,
                                  "idSpace": 44,
                                  "description": "Muy espacioso y cómodo",
                                  "score": 5,
                                  "isActive": null,
                                  "deactivationReason": null,
                                  "created_at": null
                                }
                                """
                            )
                    )
            )
            @RequestBody CommentDTO commentDTO,
            Authentication authentication){
        if (authentication.getAuthorities().stream().anyMatch(r -> Objects.equals(r.getAuthority(), "ROLE_ADMIN"))){
            //Logica si es un Admin
            commentService.modifyComment(id,commentDTO);
        }else {
            //Logica si es un client
             commentService.consumerModifyCommentOnSpace(id,commentDTO);
        }
    }

    @GetMapping("/byspaceid/{id}")
    @Operation(
            summary = "Busca TODOS los Comentarios de un espacio.",
            description = "Devuelve una lista Completa de Comentarios por el id del espacio."
    )
    public ResponseEntity<List<CommentDTO>> findAllBySpaceId(@PathVariable Integer id){
        return ResponseEntity.ok(commentService.findAllBySpaceId(id));
    }

    @GetMapping("/byconsumerid/{id}")
    @Operation(
            summary = "Busca TODOS los Comentarios de un usuario.",
            description = "Devuelve una lista Completa de Comentarios de una ID de usuario."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<CommentDTO>> findAllByConsumerId(@PathVariable Integer id){
        return ResponseEntity.ok(commentService.findAllByConsumerId(id));
    }

    @GetMapping("/byspaceid/{id}/deactivated")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Busca los comentarios desactivados de un espacio.",
            description = "Devuelve los comentarios desactivados asociados al espacio indicado. Solo accesible para administradores."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<CommentDTO>> findDeactivatedBySpaceId(@PathVariable Integer id){
        return ResponseEntity.ok(commentService.findDeactivatedCommentsBySpaceId(id));
    }

    @GetMapping("/byconsumerid/{id}/deactivated")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Busca los comentarios desactivados de un consumidor.",
            description = "Devuelve los comentarios desactivados asociados al consumidor indicado. Solo accesible para administradores."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<CommentDTO>> findDeactivatedByConsumerId(@PathVariable Integer id){
        return ResponseEntity.ok(commentService.findDeactivatedCommentsByConsumerId(id));
    }

    @GetMapping("/byscore/asc")
    @Operation(
            summary = "Busca TODOS los Comentarios.",
            description = "Devuelve una lista Completa de Comentarios de forma Ascendiente por su puntuación."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<CommentDTO>> findAllByScoreASC(){
        return ResponseEntity.ok(commentService.filterByScoreASC());
    }

    @GetMapping("/byscore/desc")
    @Operation(
            summary = "Busca TODOS los Comentarios.",
            description = "Devuelve una lista Completa de Comentarios de forma Descendiente por su puntuación."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<CommentDTO>> findAllByScoreDesc(){
        return ResponseEntity.ok(commentService.filterByScoreDESC());
    }

    @GetMapping("/spaces/{spaceId}/has-comment")
    @Operation(
            summary = "Comprueba si el usuario ya comentó un espacio",
            description = "Responde true si el usuario autenticado tiene un comentario registrado para ese espacio; de lo contrario, responde false."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Boolean> hasCommentOnSpace(
            @PathVariable Integer spaceId
    ) {
        return ResponseEntity.ok(commentService.hasCommentOnSpace(spaceId));
    }

    @GetMapping("/spaces/{spaceId}/can-comment")
    @Operation(
            summary = "Comprueba si el usuario puede comentar un espacio",
            description = "Responde true si el usuario autenticado tiene una reserva activa CONFIRMED o COMPLETED para ese espacio; de lo contrario, responde false."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Boolean> canCommentOnSpace(
            @PathVariable Integer spaceId
    ) {
        return ResponseEntity.ok(commentService.canCommentOnSpace(spaceId));
    }
}