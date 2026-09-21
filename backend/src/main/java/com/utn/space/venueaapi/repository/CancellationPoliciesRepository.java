package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.CancellationPolicies;
import com.utn.space.venueaapi.model.EPolicyType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CancellationPoliciesRepository extends JpaRepository<CancellationPolicies,Integer> {
    Optional<CancellationPolicies> findByType(EPolicyType type);
}
