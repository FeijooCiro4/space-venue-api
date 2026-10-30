package com.utn.space.venueaapi.controllers;

import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.model.Consumer;
import com.utn.space.venueaapi.model.Credential;
import com.utn.space.venueaapi.model.records.ConsumerFilterDTO;

import com.utn.space.venueaapi.service.ConsumerService;
import com.utn.space.venueaapi.service.RegistrationService;
import com.utn.space.venueaapi.service.TokenBlacklistService;
import com.utn.space.venueaapi.model.records.CredentialRegistrationDTO;
import com.utn.space.venueaapi.model.records.MessageResponseDTO;
import com.utn.space.venueaapi.model.records.RegistroDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@AllArgsConstructor
@RestController
@Tag(name = "Usuarios", description = "Operaciones sobre Consumer.")

@RequestMapping("/api")
public class ConsumerController {

    @Autowired
    private final ConsumerService consumerService;

    @Autowired
    private final RegistrationService registrationService;

    @Autowired
    private final TokenBlacklistService blacklistService;

    @PostMapping("/usuarios")
    @Operation(
            summary = "Crea un Consummer.",
            description = "Crea un nuevo usuario."
    )
    public ResponseEntity<MessageResponseDTO> createUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Usuario y contraseña sin codificar",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = CredentialRegistrationDTO.class),
                            examples = @ExampleObject(
                                    name = "Ejemplo",
                                    value = """
                                    {
                                      "username":"Pepe",
                                      "password":"contraseña-de-ejemplo"}
                                    """)
                    )
            )
            @Valid @RequestBody CredentialRegistrationDTO dto) {
        registrationService.register(new RegistroDTO("", "", "", "", dto.username(), dto.password()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MessageResponseDTO("Usuario creado exitosamente"));
    }

    @GetMapping("/usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Busca todos los usuarios.",
            description = "Devuelve una lista de todos los usuarios."
    )
    public ResponseEntity<List<Consumer>> listAllUsers() {
        return ResponseEntity.ok(consumerService.findAll());
    }

    @GetMapping("/usuarios/{id}")
    @PreAuthorize("hasRole('ADMIN') or @securityUtils.isCurrentConsumer(#id, authentication.name)")
    @Operation(
            summary = "Busca un Usuario",
            description = "Busca un usuarios usando su ID."
    )
    public ResponseEntity<Consumer> listById(@PathVariable Integer id) {
        return ResponseEntity.ok(consumerService.findById(id));
    }

    @PutMapping("/usuarios/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Cambia el estado de un Usuario.",
            description = "Cambia el estado Activo de un usuario por ID."
    )
    public ResponseEntity<MessageResponseDTO> toggleUserStatus(
            //(TO DO)Falta la logica para cambiar el estado de un usuario    
            @PathVariable Integer id, @RequestParam Boolean active) {
        return ResponseEntity.ok(new MessageResponseDTO("Estado del usuario actualizado"));
    }

    //Es para para que el admin filtre consumers
    @RequestMapping(value = "/usuarios/byfields", method = {RequestMethod.GET, RequestMethod.POST})
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Busca los Usuarios por atributos.",
            description = "Crea una lista de usuario que cumplen con los atributos dados."
    )
    public ResponseEntity<List<Consumer>> findAllByFields(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Entra los datos obligatorios de la creacion de una nueva Reserva",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = Credential.class),
                            examples = @ExampleObject(
                                    name = "Ejemplo",
                                    value = """
                                    {
                                      "username":"Pepe",
                                      "isActive": true,
                                      "passwordHash":"fatiga"}
                                    """)
                    )
            )
            @RequestBody ConsumerFilterDTO consumerFilterDTO){
        return ResponseEntity.ok(consumerService.findAllByfields(consumerFilterDTO));
    }

    @DeleteMapping("/usuarios/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Elimina un usuario por ID."
    )
    public ResponseEntity<MessageResponseDTO> deleteById(@PathVariable Integer id){
        consumerService.deleteUserLogicallyById(id);
        return ResponseEntity.ok(new MessageResponseDTO("Usuario desactivado con exito"));
    }

    @PutMapping("/usuario")
    @PreAuthorize("isAuthenticated()") // Asegura que solo usuarios con sesión activa editen su perfil
    @Operation(
            summary = "Actualiza un Usuario."
    )
    public ResponseEntity<MessageResponseDTO> updateUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Entra los datos obligatorios de la creacion de una nueva Reserva",
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = Credential.class),
                            examples = @ExampleObject(
                                    name = "Ejemplo",
                                    value = """
                                    {
                                      "username":"Pepe",
                                      "isActive": true,
                                      "passwordHash":"fatiga"}
                                    """)
                    )
            )
            @RequestBody ConsumerFilterDTO updateData,
            Principal principal) {
        String username = principal.getName();
        Consumer consumerExistente = consumerService.findByUsername(username);

        if (updateData.firstname() != null) consumerExistente.setFirstname(updateData.firstname());
        if (updateData.lastname() != null) consumerExistente.setLastname(updateData.lastname());

        if (updateData.email() != null && !updateData.email().equals(consumerExistente.getEmail())) {
            if (consumerService.existByEmail(updateData.email())) {
                throw new InvalidDataException("El correo electrónico ya se encuentra registrado por otro usuario.");
            }
            consumerExistente.setEmail(updateData.email());
        }

        if (updateData.phone() != null && !updateData.phone().equals(consumerExistente.getPhone())) {
            if (consumerService.existsByPhone(updateData.phone())) {
                throw new InvalidDataException("El telefono ya se encuentra registrado por otro usuario.");
            }
            consumerExistente.setPhone(updateData.phone());
        }

        consumerService.updateUser(consumerExistente);
        return ResponseEntity.ok(new MessageResponseDTO("Se ha actualizado correctamente tu perfil"));
    }

    @DeleteMapping("/usuario")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Elimina un Usuario.",
            description = "Hace un SoftDelete de un usuario."
    )
    public ResponseEntity<MessageResponseDTO> deleteUser(Principal principal, HttpServletRequest request) {
        String username = principal.getName();
        consumerService.deleteUserLogically(username);

        // Invalida el token usado en la baja, igual que en el logout
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            blacklistService.blacklistToken(bearerToken.substring(7));
        }
        return ResponseEntity.ok(new MessageResponseDTO("Tu cuenta ha sido desactivada correctamente."));
    }
}