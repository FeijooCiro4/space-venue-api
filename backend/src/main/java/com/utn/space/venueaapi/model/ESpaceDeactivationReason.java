package com.utn.space.venueaapi.model;

// Motivo por el que un espacio está inactivo (análogo a EDeactivationReason de las cuentas).
// En los espacios activos el campo queda en null
public enum ESpaceDeactivationReason {
    OWNER_DELETED,    // El dueño eliminó el espacio (soft delete); no se puede reactivar
    ADMIN,            // Un admin lo desactivó
    PENDING_APPROVAL; // El dueño lo creó o editó y espera que un admin lo apruebe
}
