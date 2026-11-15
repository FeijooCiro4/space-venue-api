package com.utn.space.venueaapi.model.records;

// Lo que pasaría si el admin desactiva una cuenta o la pasa a cliente (GET /usuarios/{id}/impacto).
// bloqueado = la operación se va a rechazar; motivoBloqueo dice por qué
public record AccountImpactDTO(
        int espaciosOcultos,
        int reservasACancelar,
        int reservasPagadas,
        boolean bloqueado,
        String motivoBloqueo
) {}
