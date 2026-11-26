package com.utn.space.venueaapi.config;

import java.time.LocalDateTime;
import java.time.ZoneId;

// Hora "de negocio" de la app. Por ahora la app funciona solo en Argentina:
// las fechas de las reservas (fromDate, untilDate) se cargan en hora argentina, sin zona.
// El servidor (EC2) corre en UTC, así que LocalDateTime.now() a secas queda 3 h adelantado.
// Todo lo que se compare contra fechas de reserva tiene que usar esta hora.
public final class AppClock {
    public static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private AppClock() {}

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
