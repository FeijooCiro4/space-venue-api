package com.utn.space.venueaapi.exceptions;

public class ServiceOutOfPlaceException extends RuntimeException {
    public ServiceOutOfPlaceException(String message) {
        super(message);
    }
}
