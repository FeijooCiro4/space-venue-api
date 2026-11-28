package com.utn.space.venueaapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
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
    // Defensa para otras respuestas que todavía incluyen Space (por ejemplo reservas).
    @JsonIgnoreProperties({"email", "phone", "credentials"})
    private Consumer consumerOwner;

    @ManyToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "idLocation")
    private Location location;

    @Column(name = "floor")
    private Integer floor;

    @Column(name = "name_space")
    private String nameSpace;

    private String description;

    @Column(name = "base_price")
    private BigDecimal basePrice;

    @Column(name = "publication_date")
    private LocalDate publicationDate;

    @Column(name = "buffer_time")
    private Integer bufferTime;

    private double rating;

    @Column(name = "comment_count")
    private Integer commentCount;

    private Boolean isActive;

    //Marca los espacios que el dueño eliminó (soft delete). Distinto de isActive, que también se usa para "pendiente de aprobación"
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean deletedByOwner = false;

    // Por qué el espacio está inactivo. null mientras isActive sea true.
    // Los espacios desactivados antes de agregar este campo quedan en null (salvo los eliminados por el dueño, ver PublicSpaceMapper)
    @Enumerated(EnumType.STRING)
    private ESpaceDeactivationReason deactivationReason;

    @OneToMany(mappedBy = "space", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JsonManagedReference
    private List<SpaceServiceItem> services;

    // El dueño puede tener espacios publicados si su cuenta está activa y es OFFERER o ADMIN.
    // No se guarda en el espacio: si el dueño se reactiva o recupera el rol, sus espacios vuelven solos
    @JsonIgnore
    public boolean isOwnerAvailable() {
        if (consumerOwner == null || consumerOwner.getCredentials() == null) return false;
        Credential c = consumerOwner.getCredentials();
        return Boolean.TRUE.equals(c.getIsActive())
                && (c.getRol() == ERoles.ROLE_OFFERER || c.getRol() == ERoles.ROLE_ADMIN);
    }

    // Un espacio se muestra en los listados públicos y se puede reservar solo si está activo y su dueño está disponible.
    // Las queries de SpaceRepository repiten esta regla (ver SpaceRepository.BOOKABLE)
    @JsonIgnore
    public boolean isBookable() {
        return Boolean.TRUE.equals(isActive) && isOwnerAvailable();
    }
}
