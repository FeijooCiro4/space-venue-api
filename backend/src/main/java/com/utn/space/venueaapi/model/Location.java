package com.utn.space.venueaapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@AllArgsConstructor
@Data
@Table(name = "locations")
@Entity
@NoArgsConstructor
public class Location {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_location")
    private Integer idLocation;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(precision = 8, scale = 6)
    private BigDecimal latitude;
}
