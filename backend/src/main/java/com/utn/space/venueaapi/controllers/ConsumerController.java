package com.utn.space.venueaapi.controllers;

import com.utn.space.venueaapi.model.Consumer;
import com.utn.space.venueaapi.model.Credential;
import com.utn.space.venueaapi.model.ERoles;
import com.utn.space.venueaapi.model.records.ConsumerFilterDTO;

import com.utn.space.venueaapi.service.ConsumerService;
import com.utn.space.venueaapi.service.CredentialService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final CredentialService credentialService;
    @Autowired
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/usuarios")
    @Operation(
            summary = "Crea un Consummer.",
            description = "Crea un nuevo usuario."
    )
    public ResponseEntity<String> createUser(
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
            @RequestBody Credential credential) {
        // Se encripta la contraseña recibida y se asigna de nuevo al objeto
        String passwordEncriptada = passwordEncoder.encode(credential.getPassword());
        credential.setPassword(passwordEncriptada);

        // Validar duplicados
        if (credentialService.existsByUsername(credential.getUsername())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("{\"error\": \"El nombre de usuario ya se encuentra registrado.\"}");
        }

        // Configurar la credencial de forma explícita
        credential.setRol(ERoles.ROLE_CLIENT);
        credential.setIsActive(Boolean.TRUE);

        // Guardar la credencial en su repositorio
        credentialService.saveCredential(credential);

        // Forzar la asignación del ID en el objeto Consumer
        Consumer nuevoConsumer = new Consumer();

        // Vincular la credencial que ya tiene el ID asignado y en limpio
        nuevoConsumer.setCredentials(credential);

        // Inicializar las cadenas vacías obligatorias
        nuevoConsumer.setFirstname("");
        nuevoConsumer.setLastname("");
        nuevoConsumer.setEmail("");
        nuevoConsumer.setPhone("");

        // Guardar y flushear en la tabla 'consumers'
        consumerService.saveConsumer(nuevoConsumer);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body("{\"mensaje\": \"Usuario creado exitosamente\"}");
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
    @PreAuthorize("hasRole('CLIENT') or hasRole('ADMIN')")
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
    public ResponseEntity<String> toggleUserStatus(
            @PathVariable Integer id, @RequestParam Boolean active) {
        // Tu lógica pendiente de service
        return ResponseEntity.ok("Estado del usuario actualizado");
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
    public ResponseEntity<String> deleteById(@PathVariable Integer id){
        consumerService.deleteUserLogicallyById(id);
        return ResponseEntity.ok("Usuario desactivado con exito");
    }

    @PutMapping("/usuario")
    @PreAuthorize("isAuthenticated()") // Asegura que solo usuarios con sesión activa editen su perfil
    @Operation(
            summary = "Actualiza un Usuario."
    )
    public ResponseEntity<String> updateUser(
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
                throw new RuntimeException("El correo electrónico ya se encuentra registrado por otro usuario.");
            }
            consumerExistente.setEmail(updateData.email());
        }

        if (updateData.phone() != null && !updateData.phone().equals(consumerExistente.getPhone())) {
            if (consumerService.existsByPhone(updateData.phone())) {
                throw new RuntimeException("El telefono ya se encuentra registrado por otro usuario.");
            }
            consumerExistente.setPhone(updateData.phone());
        }

        consumerService.updateUser(consumerExistente);
        return ResponseEntity.ok("Se ha actualizado correctamente tu perfil");
    }

    @DeleteMapping("/usuario")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Elimina un Usuario.",
            description = "Hace un SoftDelete de un usuario."
    )
    public ResponseEntity<String> deleteUser(Principal principal) {
        String username = principal.getName();
        consumerService.deleteUserLogically(username);
        return ResponseEntity.ok("Tu cuenta ha sido desactivada correctamente.");
    }
}