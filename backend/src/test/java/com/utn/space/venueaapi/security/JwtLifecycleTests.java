package com.utn.space.venueaapi.security;

import com.utn.space.venueaapi.model.RevokedToken;
import com.utn.space.venueaapi.repository.RevokedTokenRepository;
import com.utn.space.venueaapi.service.TokenBlacklistService;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.utn.space.venueaapi.model.Consumer;
import com.utn.space.venueaapi.model.Credential;
import com.utn.space.venueaapi.repository.ConsumerRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtLifecycleTests {
    @Autowired RevokedTokenRepository revokedTokens;
    @Autowired ConsumerRepository consumers;
    @Autowired JwtUtil applicationJwt;
    @Autowired MockMvc mvc;
    @Value("${app.jwt.secret-base64}") String secret;

    @Test
    void sameConfiguredKeySurvivesNewInstancesAndLoginsHaveDistinctTokens() {
        Clock clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
        JwtUtil firstInstance = new JwtUtil(secret, clock);
        JwtUtil secondInstance = new JwtUtil(secret, clock);
        String token = firstInstance.generarToken("client", "ROLE_CLIENT", 1);
        assertTrue(secondInstance.validarToken(token, "client"));
        assertEquals(1, secondInstance.extraerConsumerId(token));
        assertNotEquals(token, firstInstance.generarToken("client", "ROLE_CLIENT", 1));
        assertNotEquals(firstInstance.generarToken("client", "ROLE_CLIENT"),
                firstInstance.generarToken("client", "ROLE_CLIENT"));
        assertThrows(RuntimeException.class, () -> new JwtUtil(
                "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=", clock).extraerClaims(token));
    }

    @Test
    void missingOrWeakSigningKeyFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil(""));
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil(" "));
        assertThrows(RuntimeException.class, () -> new JwtUtil("c2hvcnQ="));
    }

    @Test
    void revocationSurvivesServiceRecreationAndLastsUntilActualExpiration() {
        Instant issuedAt = Instant.now().plus(Duration.ofDays(2)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Clock initialClock = Clock.fixed(issuedAt, ZoneOffset.UTC);
        JwtUtil issuer = new JwtUtil(secret, initialClock);
        String token = issuer.generarToken("client", "ROLE_CLIENT", 1);
        Instant expiresAt = issuer.extraerClaims(token).getExpiration().toInstant();
        TokenBlacklistService firstInstance = new TokenBlacklistService(revokedTokens, issuer, initialClock);
        firstInstance.blacklistToken(token);
        revokedTokens.flush();
        RevokedToken stored = revokedTokens.findAll().stream()
                .filter(row -> row.getExpiresAt().equals(expiresAt)).findFirst().orElseThrow();
        assertEquals(64, stored.getTokenHash().length());
        assertNotEquals(token, stored.getTokenHash());

        Clock laterClock = Clock.fixed(issuedAt.plus(Duration.ofHours(9)), ZoneOffset.UTC);
        JwtUtil restartedJwt = new JwtUtil(secret, laterClock);
        TokenBlacklistService restartedService = new TokenBlacklistService(revokedTokens, restartedJwt, laterClock);
        assertTrue(restartedJwt.validarToken(token, "client"));
        assertTrue(restartedService.isTokenBlacklisted(token));
        restartedService.cleanExpiredTokens();
        assertTrue(restartedService.isTokenBlacklisted(token));

        Clock expiredClock = Clock.fixed(expiresAt.plusSeconds(1), ZoneOffset.UTC);
        JwtUtil expiredJwt = new JwtUtil(secret, expiredClock);
        TokenBlacklistService afterExpiration = new TokenBlacklistService(revokedTokens, expiredJwt, expiredClock);
        assertThrows(ExpiredJwtException.class, () -> expiredJwt.extraerClaims(token));
        afterExpiration.cleanExpiredTokens();
        assertFalse(revokedTokens.existsById(stored.getTokenHash()));
    }

    @Test
    void invalidAndExpiredTokensCannotReachLogout() throws Exception {
        long originalCount = revokedTokens.count();
        JwtUtil pastIssuer = new JwtUtil(secret, Clock.fixed(Instant.now().minus(Duration.ofDays(1)), ZoneOffset.UTC));
        String expired = pastIssuer.generarToken("client", "ROLE_CLIENT", 1);
        for (String token : new String[]{"not-a-jwt", expired}) {
            mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        assertEquals(originalCount, revokedTokens.count());
    }

    @Test
    void logoutBlocksOnlyThatSessionAndKeepsOtherSessionActive() throws Exception {
        Credential credential = new Credential();
        credential.setUsername("logout-client");
        credential.setPassword("unused-test-password");
        Consumer consumer = new Consumer();
        consumer.setCredentials(credential);
        consumers.saveAndFlush(consumer);
        String token = applicationJwt.generarToken(credential.getUsername(), "ROLE_CLIENT", consumer.getIdConsumer());
        String otherToken = applicationJwt.generarToken(credential.getUsername(), "ROLE_CLIENT", consumer.getIdConsumer());
        mvc.perform(get("/api/reservations/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reservations/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reservations/me").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk());
    }
}
