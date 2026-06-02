package com.utn.space.venueaapi.Repository;

import com.utn.space.venueaapi.Model.Reservations;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository

public interface ReservationRepository extends JpaRepository<Reservations,Long> {
}
