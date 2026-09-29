package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location,Integer> {
    Optional<Location> findLocationByLongitudeAndLatitude(BigDecimal longitude, BigDecimal latitude);
}
