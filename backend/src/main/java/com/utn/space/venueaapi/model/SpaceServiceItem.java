package com.utn.space.venueaapi.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "services")
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@EqualsAndHashCode @ToString
public class SpaceServiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_service")
    private Integer id;

    private String description;
    private BigDecimal price;
    private Boolean isActive;

    @ManyToOne
    @JoinColumn(name = "idSpace")
    @JsonBackReference
    private Space space;
}
