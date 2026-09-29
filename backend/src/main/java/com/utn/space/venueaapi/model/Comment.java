package com.utn.space.venueaapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@AllArgsConstructor
@Data
@Entity
@Table(name = "comments")
@NoArgsConstructor
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comment")
    private Integer idComment;

    @ManyToOne
    @JoinColumn(name = "idConsumer") @JsonIgnoreProperties
    private Consumer consumer;

    @ManyToOne
    @JoinColumn(name = "idSpace") @JsonIgnoreProperties //Por si Spring Boot entra en bucle
    private Space space;

    private String description;

    @Column(name = "score", nullable = false)
    private Byte score;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}