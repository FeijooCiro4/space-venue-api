package com.utn.space.venueaapi.controllers;

import com.utn.space.venueaapi.model.Consumer;
import com.utn.space.venueaapi.security.JwtUtil;
import com.utn.space.venueaapi.service.ConsumerService;
import com.utn.space.venueaapi.service.RegistrationService;
import com.utn.space.venueaapi.service.TokenBlacklistService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RegistrationService registrationService;
    private final ConsumerService consumerService;

    @Autowired
    private TokenBlacklistService blacklistService;

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String jwt = bearerToken.substring(7);

            blacklistService.blacklistToken(jwt);

            return ResponseEntity.ok("Sesión cerrada exitosamente y token invalidado.");
        }
        return ResponseEntity.badRequest().body("Token no provisto o inválido.");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody java.util.Map<String, String> loginRequest) {
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
            return ResponseEntity.ok("Bearer " + token);
        } catch (BadCredentialsException | DisabledException e) {
            return ResponseEntity.status(401).body("Credenciales inválidas");
        }
    }

    // Cambio el @RequestBody para que reciba record RegistroDTO
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody com.utn.space.venueaapi.model.records.RegistroDTO dto) {

        registrationService.register(dto);

        return ResponseEntity.status(201).body("Usuario y perfil registrados con éxito");
    }
}
