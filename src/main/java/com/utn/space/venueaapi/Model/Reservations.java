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
public class Reservations {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id_Reservation;

    private LocalDateTime from_date;
    private LocalDateTime until_date;
    private Double final_price;


    @ManyToOne
    @JoinColumn(name = "id_space")
    @JsonIgnore
    private Spaces space;

    @ManyToOne
    @JoinColumn(name = "id_consumer")
    @JsonIgnore
    private Consumers consumer;
}
