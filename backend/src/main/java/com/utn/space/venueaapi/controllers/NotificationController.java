package com.utn.space.venueaapi.controllers;

import com.utn.space.venueaapi.model.Notification;
import com.utn.space.venueaapi.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;
import java.time.ZoneOffset;

@Tag(name = "Notificaciones", description = "Operaciones sobre Notificaciones.")

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    @Autowired
    private NotificationService notificationService;

///------------------------------------------------Metodos--------------------------------------------------------------------
    @GetMapping
    @Operation(
            summary = "Busca todas las notificaciones (solo para ADMIN).",
            description = "Devuelve la lista de TODAS las Notificaciones (solo accesible para administradores)."
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> listAll (){
        List<Map<String, Object>> out = notificationService.listAll().stream().map(this::toDto).toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping("/me")
    @Operation(
            summary = "Busca notificaciones del usuario autenticado.",
            description = "Devuelve las notificaciones del usuario logueado."
    )
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getMyNotifications(){
        List<Map<String, Object>> out = notificationService.listAllByIdConsumerForConsumer().stream().map(this::toDto).toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping("/unread-count")
    @Operation(
            summary = "Cuenta las notificaciones no vistas del usuario logueado.",
            description = "Devuelve un objeto JSON con el conteo de notificaciones donde isSeen = false."
    )
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<java.util.Map<String, Long>> getUnreadCount() {
        long count = notificationService.countUnseenForConsumer();
        return ResponseEntity.ok(java.util.Map.of("count", count));
    }

    @GetMapping("/consumer/{id}")
    @Operation(
            summary = "Busca todas las notificaciones de un Usuario.",
            description = "Devuelve la lista de las Notificaciones un Usuario."
    )
    @PreAuthorize("hasAnyRole('CLIENT', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>>findAllByIdConsumer(@Parameter(description = "ID del Usuario") @PathVariable Integer id, Authentication authentication){
        List<Notification> list;
        if (authentication.getAuthorities().stream().anyMatch(r -> Objects.equals(r.getAuthority(), "ROLE_ADMIN"))){
            //Logica si es un Admin
            list = notificationService.listAllByIdConsumer(id);
        } else {
            //El cliente solo puede ver sus propias notificaciones
            list = notificationService.listAllByIdConsumerForConsumer();
        }
        return ResponseEntity.ok(list.stream().map(this::toDto).toList());
    }

    @GetMapping("/consumer/onlyunseen")
    @Operation(
            summary = "Busca todas las notificaciones, no vistas, de un Usuario.",
            description = "Devuelve la lista de las Notificaciones con el Atributo (isSeen = false) un Usuario."
    )
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<List<Map<String, Object>>> findAllUnseenByIdconsumer(){
        List<Map<String, Object>> out = notificationService.listAllUnseenForConsumer().stream().map(this::toDto).toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Busca una notificación en particular.",
            description = "Busca una notificación por su ID."
    )
    public ResponseEntity<Map<String, Object>> findById(@Parameter(description = "ID de la notificación") @PathVariable Integer id){
        Notification n = notificationService.findById(id);
        return ResponseEntity.status(HttpStatus.OK).body(toDto(n));
    }


    @PostMapping("/{id}")

    @Operation(
            summary = "Marca como vista a una notificación",
            description = "Busca una notificación por su ID y la marca como vista."
    )
    public ResponseEntity<Map<String, Object>> markAsSeen (@Parameter(description = "ID del notificación") @PathVariable Integer id){
        Notification updated = notificationService.markAsSeen(id);
        return ResponseEntity.status(HttpStatus.OK).body(toDto(updated));
    }

    // --- Helper para serializar Notification con timestamp UTC en milisegundos ---
    private Map<String, Object> toDto(Notification n) {
        Map<String, Object> m = new HashMap<>();
        m.put("idNotification", n.getIdNotification());
        m.put("id", n.getIdNotification());
        m.put("message", n.getMessage());
        m.put("isSeen", n.getIsSeen());
        m.put("seen", n.getIsSeen());
        Long createdMillis = null;
        if (n.getCreatedAt() != null) {
            createdMillis = n.getCreatedAt().atOffset(ZoneOffset.UTC).toInstant().toEpochMilli();
        }
        m.put("createdAt", createdMillis);
        m.put("type", null);
        m.put("detail", null);
        return m;
    }
}
