package com.utn.space.venueaapi.exceptions;

public class ReservationLimitException extends RuntimeException {
    public ReservationLimitException(String message) {
        super(message);
    }
}

