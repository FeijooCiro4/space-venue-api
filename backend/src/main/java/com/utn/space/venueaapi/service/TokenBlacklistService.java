package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.model.RevokedToken;
import com.utn.space.venueaapi.repository.RevokedTokenRepository;
import com.utn.space.venueaapi.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class TokenBlacklistService {
    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtUtil jwtUtil;
    private final Clock clock;

    @Autowired
    public TokenBlacklistService(RevokedTokenRepository revokedTokenRepository, JwtUtil jwtUtil) {
        this(revokedTokenRepository, jwtUtil, Clock.systemUTC());
    }

    public TokenBlacklistService(RevokedTokenRepository revokedTokenRepository, JwtUtil jwtUtil, Clock clock) {
        this.revokedTokenRepository = revokedTokenRepository;
        this.jwtUtil = jwtUtil;
        this.clock = clock;
    }

    @Transactional
    public void blacklistToken(String token) {
        Instant expiration = jwtUtil.extraerClaims(token).getExpiration().toInstant();
        // Persistir solo la huella, nunca el token reutilizable, hasta su vencimiento real.
        revokedTokenRepository.save(new RevokedToken(tokenHash(token), expiration));
    }

    public boolean isTokenBlacklisted(String token) {
        return revokedTokenRepository.existsByTokenHashAndExpiresAtAfter(tokenHash(token), clock.instant());
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanExpiredTokens() {
        revokedTokenRepository.deleteByExpiresAtLessThanEqual(clock.instant());
    }

    private String tokenHash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
