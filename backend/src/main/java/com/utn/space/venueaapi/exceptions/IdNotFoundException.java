package com.utn.space.venueaapi.exceptions;
//Para crear un mensaje cuando no se encuentra por id algun "recurso", se reutiliza en varios lugares
public class IdNotFoundException extends RuntimeException {
    public IdNotFoundException(String recurso, Integer id) {
        super("No se encontró " + recurso + " con id " + id + ".");
    }
}
