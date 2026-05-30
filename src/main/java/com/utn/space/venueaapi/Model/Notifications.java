package com.utn.space.venueaapi.Model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor

@Entity
public class Notifications {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id_Notification;

    private LocalDateTime fecha;
    private String contenido;
    private Boolean visto=false;

    @ManyToOne
    @JoinColumn(name = "id_consumer")
    @JsonIgnore
    private Consumers consumer;


}
