package com.utn.space.venueaapi.Repository;

import com.utn.space.venueaapi.Model.Consumers;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository

public interface ConsumersRepository extends JpaRepository<Consumers,Long> {
}
