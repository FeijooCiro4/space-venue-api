package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.PaymentModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentModel,Long> {
    boolean existsByReservation_Id(Integer idReservation);
    // status es el estado de Mercado Pago ("approved", "rejected", ...)
    boolean existsByReservation_IdAndStatus(Integer idReservation, String status);

    PaymentModel findByReservation_Id(Integer idReservation);
}
