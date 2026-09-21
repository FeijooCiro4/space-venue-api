package com.utn.space.venueaapi.exceptions;

public class SpaceUnavailableException extends RuntimeException {
    public SpaceUnavailableException(String message) {
        super(message);
    }
}
