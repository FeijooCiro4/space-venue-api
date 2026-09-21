package com.utn.space.venueaapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@AllArgsConstructor
@Data
@Table(name = "cancellationpolicies")
@Entity
@NoArgsConstructor
public class CancellationPolicies {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cancellation_policies")
    private Integer idCancellationPolicies;

    @Enumerated(EnumType.STRING)
    private EPolicyType type;

    @Column(name = "days_anticipation")
    private Integer daysAnticipation;
    @Column(name = "refund_percentage")
    private BigDecimal refundPercentage;
}
