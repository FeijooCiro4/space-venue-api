package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {
    boolean existsByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
    void deleteByExpiresAtLessThanEqual(Instant now);
}
