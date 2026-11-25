package com.utn.space.venueaapi.model;

// Estado del comentario del usuario logueado en un espacio, para saber si puede escribir uno nuevo
public enum ECommentStatus {
    NONE,       // No tiene comentario (o solo tiene comentarios que eliminó él mismo): puede comentar
    ACTIVE,     // Tiene su comentario publicado: puede modificarlo o eliminarlo
    MODERATED   // Un admin desactivó su comentario: no puede volver a comentar en el espacio
}
