package com.utn.space.venueaapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/// Este objeto guarda los servicios asociados a una cuenta en el momento de su reserva con el precio que tenian
@Entity
@Table(name = "servicesselected")
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@EqualsAndHashCode @ToString
public class ServiceSelected {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idServiceSelected")
    private Integer id;

    @Column(name = "price_at_reservation")
    private BigDecimal priceAtReservation;

    @Column(name = "descriptionFrozen")
    String descriptionFrozen;

    @ManyToOne
    @JoinColumn(name = "id_reservation")
    @ToString.Exclude
    @JsonIgnore
    private Reservation reservation;

    public ServiceSelected(SpaceServiceItem item, Reservation res) {
        this.priceAtReservation = item.getPrice();
        this.descriptionFrozen=item.getDescription();
        this.reservation=res;
    }
}
