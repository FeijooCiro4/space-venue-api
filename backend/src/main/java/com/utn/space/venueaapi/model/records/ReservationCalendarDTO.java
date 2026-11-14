package com.utn.space.venueaapi.model.records;

import com.utn.space.venueaapi.model.ReservationStatus;

import java.time.LocalDateTime;

/**
 * Vista reducida de una reserva para el calendario de disponibilidad de un espacio.
 * id, title y consumerName solo se completan para el dueño del espacio, el admin
 * o el propio cliente de la reserva; para el resto de los usuarios quedan en null
 * y solo se expone el horario ocupado y su estado.
 */
public record ReservationCalendarDTO(
        Integer id,
        LocalDateTime fromDate,
        LocalDateTime untilDate,
        ReservationStatus status,
        String title,
        String consumerName
) {}
