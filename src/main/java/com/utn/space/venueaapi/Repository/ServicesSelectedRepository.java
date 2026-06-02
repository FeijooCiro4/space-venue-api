package com.utn.space.venueaapi.Repository;

import com.utn.space.venueaapi.model.Services_selected;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository

public interface ServicesSelectedRepository extends JpaRepository<Services_selected,Long> {
}
