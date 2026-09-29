package com.utn.space.venueaapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "reservations")
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reservation")
    private Integer id;

    private String title;
    private String description;


    @Column(name = "from_date")
    private LocalDateTime fromDate;

    @Column(name = "until_date")
    private LocalDateTime untilDate;

    @Column(name = "final_price")
    private BigDecimal finalPrice;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @JsonIgnore
    private Boolean isActive=true;



    @ManyToOne
    @JoinColumn(name = "idConsumer")
    @JsonIgnoreProperties({"reservations", "password", "credentials"}) // Rompe con el bucle infinito de mapeo, el cual resulta en un código de error 403
    private Consumer consumer;

    @ManyToOne
    @JoinColumn(name = "idSpace")
    private Space space;


    // Mapea contra el atributo 'reservation' de la clase intermedia
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceSelected> services;

}
