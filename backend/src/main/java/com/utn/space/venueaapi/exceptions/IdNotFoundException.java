package com.utn.space.venueaapi.exceptions;

public class IdNotFoundException extends RuntimeException {
    public IdNotFoundException(String objet, Integer id) {
        super("El id: " + id + " de " + objet + " no existe.");
    }
}
