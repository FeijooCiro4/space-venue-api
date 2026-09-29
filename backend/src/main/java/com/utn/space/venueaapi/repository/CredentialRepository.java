package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.Credential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CredentialRepository extends JpaRepository<Credential, String> {
    Optional<Credential> findByUsername(String username);
    boolean existsByUsername(String username);
}
