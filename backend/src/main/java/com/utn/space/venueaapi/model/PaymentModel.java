package com.utn.space.venueaapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentModel {

    @Id
    @Column(name = "id_payment")
    private Long idPayment; // ID provisto directamente por Mercado Pago

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_reservation", nullable = false)
    private Reservation reservation;

    @Column(name = "payment_method_type", length = 50)
    private String paymentMethodType;

    @Column(name = "payment_method_id", length = 50)
    private String paymentMethodId;

    @Column(length = 30, nullable = false)
    private String status;

    @Column(name = "status_detail", length = 100)
    private String statusDetail;

    @Column(name = "currency_code", length = 10)
    private String currencyCode;

    @Column(name = "transaction_amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal transactionAmount;

    @Column(name = "total_refunded_amount", precision = 10, scale = 2)
    private BigDecimal totalRefundedAmount = BigDecimal.ZERO;

    @Column(name = "net_received_amount", precision = 10, scale = 2)
    private BigDecimal netReceivedAmount;

    @Column(name = "external_reference", length = 100)
    private String externalReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Relación bidireccional opcional con los reembolsos
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PaymentRefund> refunds;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
