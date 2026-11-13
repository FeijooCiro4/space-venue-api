package com.utn.space.venueaapi.service.mappers;

import com.utn.space.venueaapi.model.Comment;
import com.utn.space.venueaapi.model.records.CommentDTO;

public class CommentMapper {
    public static CommentDTO mapToDTO(Comment comment) {
        String username = "";

        if (comment.getConsumer() != null && comment.getConsumer().getCredentials() != null) {
            username = comment.getConsumer().getCredentials().getUsername();
        } else {
            username = "No se encontró el usuario.";
        }

        CommentDTO dto = new CommentDTO(
                comment.getIdComment(),
                comment.getConsumer().getIdConsumer(),
                username,
                comment.getSpace().getIdSpace(),
                comment.getDescription(),
                comment.getScore(),
                comment.getIsActive(),
                comment.getDeactivationReason() != null ? comment.getDeactivationReason().name() : null,
                comment.getCreatedAt());

        return dto;
    }

}
