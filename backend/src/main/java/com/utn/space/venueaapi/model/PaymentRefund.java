package com.utn.space.venueaapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_refunds")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefund {

    @Id
    @Column(name = "id_payment_refund")
    private Long idPaymentRefund; // ID único del reembolso generado por Mercado Pago

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_payment", nullable = false)
    private PaymentModel payment;

    @Column(name = "amount_refunded", precision = 10, scale = 2, nullable = false)
    private BigDecimal amountRefunded;

    @Column(length = 30, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}