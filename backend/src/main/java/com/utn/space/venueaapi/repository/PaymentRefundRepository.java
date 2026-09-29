package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund,Long> {
}
