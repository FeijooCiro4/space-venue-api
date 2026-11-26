package com.utn.space.venueaapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Formula;

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

    // Por qué se canceló (solo cuando status es CANCELLED). null en las canceladas antes de existir el campo
    @Enumerated(EnumType.STRING)
    private ECancellationReason cancellationReason;

    // true si se canceló estando pagada y le corresponde un reembolso al cliente (refundAmount > 0).
    // Por ahora el reembolso NO se hace automáticamente: este flag marca las reservas que quedan pendientes de reintegro
    private Boolean refundPending = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Resultado de la política de cancelación, guardado al cancelar. Solo si estaba pagada; null en el resto
    @Column(name = "refund_percentage")
    private Integer refundPercentage;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    // true si tiene un pago aprobado. Se calcula al leer la reserva (no es una columna ni se puede modificar):
    // el front lo usa para mostrar "Confirmada y pagada"
    @Formula("(select count(*) > 0 from payments p where p.id_reservation = id_reservation and p.status = 'approved')")
    private Boolean paid;

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
