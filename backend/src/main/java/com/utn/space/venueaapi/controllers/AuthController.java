package com.utn.space.venueaapi.controllers;

import com.mercadopago.net.HttpStatus;
import com.utn.space.venueaapi.model.Consumer;
import com.utn.space.venueaapi.model.records.ErrorResponseDTO;
import com.utn.space.venueaapi.model.records.LoginRequestDTO;
import com.utn.space.venueaapi.model.records.LoginResponseDTO;
import com.utn.space.venueaapi.model.records.MessageResponseDTO;
import com.utn.space.venueaapi.security.JwtUtil;
import com.utn.space.venueaapi.service.ConsumerService;
import com.utn.space.venueaapi.service.NotificationService;
import com.utn.space.venueaapi.service.RegistrationService;
import com.utn.space.venueaapi.service.TokenBlacklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RegistrationService registrationService;
    private final ConsumerService consumerService;
    private final NotificationService notificationService;

    @Autowired
    private TokenBlacklistService blacklistService;

    @PostMapping("/logout")//La respuesta esta en ? porque se pueden devolver 2 DTO distintos
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String jwt = bearerToken.substring(7);

            blacklistService.blacklistToken(jwt);

            return ResponseEntity.ok(new MessageResponseDTO("Sesión cerrada exitosamente y token invalidado."));
        }
        return ResponseEntity.badRequest().body(new ErrorResponseDTO(400, "Bad Request", "Token no provisto o inválido."));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Inicia sesión",
            description = "Autentica al usuario y devuelve un JWT, su tipo, rol e identificador de consumidor."
    )
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = LoginRequestDTO.class))
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inicio de sesión exitoso",
                    content = @Content(schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "Usuario o contraseña incorrectos",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<?> login(@RequestBody java.util.Map<String, String> loginRequest) {
        try {
            String username = loginRequest.get("username");
            String password = loginRequest.get("password");

            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            // Se extrae el rol REAL que Spring Security cargó desde la base de datos
            String rol = auth.getAuthorities().stream()
                    .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                    .findFirst()
                    .orElse("ROLE_CLIENT"); // Rol por defecto si el usuario no tuviera ninguno

            // Obtener el consumerId del usuario autenticado
            Consumer consumer = consumerService.findByCredentialsUsername(username);
            Integer consumerId = consumer.getIdConsumer();

            String token = jwtUtil.generarToken(username, rol, consumerId);
            return ResponseEntity.ok(new LoginResponseDTO(token, "Bearer", rol, consumerId));
        } catch (BadCredentialsException | DisabledException e) {
            return ResponseEntity.status(401).body(new ErrorResponseDTO(401, "Unauthorized", "Usuario o contraseña incorrectos."));
        }
    }

    // Cambio el @RequestBody para que reciba record RegistroDTO
    //Se usa el @valid que faltaba en los registros para que se validen las restricciones de los campos del DTO.
    @PostMapping("/register")
    @Operation(
            summary = "Registra un usuario y su perfil",
            description = "Registra nombre, apellido, email, teléfono, usuario y contraseña."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario y perfil registrados",
                    content = @Content(schema = @Schema(implementation = MessageResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Conflicto con un dato ya registrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    public ResponseEntity<MessageResponseDTO> register(@Valid @RequestBody com.utn.space.venueaapi.model.records.RegistroDTO dto) {

        registrationService.register(dto);
        // La notificación de bienvenida no debe hacer fallar el registro:
        // el usuario ya quedó guardado en la transacción de registrationService.register().
        try {
            Consumer consumer = consumerService.findByCredentialsUsername(dto.username());
            notificationService.createNotification(
                    consumer,
                    """ 
                     ¡Bienvenido a Space&Venue!
                     Gracias por sumarte a Space&Venue, la plataforma para encontrar y publicar espacios para eventos, reuniones y producciones. Tu cuenta ya está lista para usarse.
                     Acá podés:
                     -Explorar la lista de espacios disponibles y ordenarlos por nombre o por precio para encontrar el que mejor se adapte a lo que buscás.
                     -Publicar tu propio espacio desde “Crear un espacio”: cargá su nombre, descripción, precio base, tiempo de receso entre reservas y ubicación.
                     -Gestionar lo que publicaste desde “Mis espacios”.
                     -Mantener tus datos al día desde “Mi perfil”.
                     
                     Cada novedad importante te va a llegar como una notificación en esta misma sección, así que fijate de vez en cuando en el ícono del sobre de la barra superior.
                     Si tenés dudas o algo no funciona como esperabas, escribinos. Estamos para ayudarte.
                     ¡Que disfrutes la experiencia!
        
                    -El equipo de Space&Venue"""
            );
        } catch (Exception e) {
            log.error("No se pudo crear la notificación de bienvenida para '{}'", dto.username(), e);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(new MessageResponseDTO("Usuario y perfil registrados con éxito"));
    }
}
