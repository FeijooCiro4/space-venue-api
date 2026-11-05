package com.utn.space.venueaapi.model;

// Motivo por el que una cuenta está inactiva. En las cuentas activas el campo queda en null
public enum EDeactivationReason {
    USER_REQUEST, // El propio usuario dio de baja su cuenta
    ADMIN;        // Un admin la desactivó
}
