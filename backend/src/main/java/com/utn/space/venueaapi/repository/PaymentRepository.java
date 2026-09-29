package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.PaymentModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentModel,Long> {
    boolean existsByReservation_Id(Integer idReservation);

    PaymentModel findByReservation_Id(Integer idReservation);
}
