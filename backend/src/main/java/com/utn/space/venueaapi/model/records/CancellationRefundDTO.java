package com.utn.space.venueaapi.model.records;

import java.math.BigDecimal;

// Resultado de aplicar la política de cancelación (vista previa y lo que se guarda al cancelar).
// paid = false si la reserva no tiene un pago aprobado: no hay nada que reintegrar
public record CancellationRefundDTO(boolean paid, int refundPercentage, BigDecimal refundAmount) {}
