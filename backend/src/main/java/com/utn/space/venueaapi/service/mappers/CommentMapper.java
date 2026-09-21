package com.utn.space.venueaapi.service.mappers;

import com.utn.space.venueaapi.model.Comment;
import com.utn.space.venueaapi.model.records.CommentDTO;

public class CommentMapper {
    public static CommentDTO mapToDTO(Comment comment) {
        String username = "";

        if (comment.getConsumer() != null && comment.getConsumer().getCredentials() != null) {
            username = comment.getConsumer().getCredentials().getUsername();
        } else {
            username = "Usuario no encontrado";
        }

        CommentDTO dto = new CommentDTO(
                comment.getIdComment(),
                comment.getConsumer().getIdConsumer(),
                username,
                comment.getSpace().getIdSpace(),
                comment.getDescription(),
                comment.getScore(),
                comment.getCreatedAt());

        return dto;
    }

}
