package com.utn.space.venueaapi.Repository;

import com.utn.space.venueaapi.model.Spaces;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository

public interface SpacesRepository extends JpaRepository<Spaces,Long> {
}
