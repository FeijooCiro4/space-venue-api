package com.utn.space.venueaapi.Repository;

import com.utn.space.venueaapi.model.Services;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository

public interface ServicesRepository extends JpaRepository<Services,Long> {
}
