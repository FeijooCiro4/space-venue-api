package com.utn.space.venueaapi.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.utn.space.venueaapi.model.flags.Create;
import com.utn.space.venueaapi.model.flags.Update;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(name = "spaces")
public class Space {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_space")
    private Integer idSpace;

    @ManyToOne
    @JoinColumn(name = "idConsumerOwner")
    private Consumer consumerOwner;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "idLocation")
    private Location location;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "idCancellationPolicies")
    private CancellationPolicies cancellationPolicies;


    @Column(name = "name_space")
    private String nameSpace;

    private String description;

    @Column(name = "base_price")
    private BigDecimal basePrice;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(name = "buffer_time")
    private Integer bufferTime;

    private Boolean isActive;


    @OneToMany(mappedBy = "space", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JsonManagedReference
    private List<SpaceServiceItem> services;
}
