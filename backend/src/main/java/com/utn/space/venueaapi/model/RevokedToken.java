package com.utn.space.venueaapi.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "revoked_tokens", indexes = @Index(name = "idx_revoked_token_expiry", columnList = "expires_at"))
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RevokedToken {
    @Id
    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
