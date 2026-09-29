package com.utn.space.venueaapi.exceptions;

public class SelfReservationException extends RuntimeException {
    public SelfReservationException(String message) {
        super(message);
    }
}
