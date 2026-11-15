package com.utn.space.venueaapi.model;

// Por qué se canceló una reserva. Solo tiene valor cuando el estado es CANCELLED.
// Las reservas canceladas antes de agregar este campo quedan en null
public enum ECancellationReason {
    CLIENT,           // La canceló el cliente
    OWNER,            // La canceló el dueño, o eliminó el espacio
    EXPIRED,          // Seguía pendiente (TENTATIVE) cuando llegó la fecha de inicio
    PAYMENT_REJECTED, // Mercado Pago rechazó el pago
    PLATFORM          // Un admin la canceló, o dio de baja el espacio o al dueño
}
