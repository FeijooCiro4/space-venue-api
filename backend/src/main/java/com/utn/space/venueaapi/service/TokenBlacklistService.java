package com.utn.space.venueaapi.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlacklistService {

    // Almacena: Token -> Timestamp de expiración
    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    public void blacklistToken(String token, long remainingTimeMs) {
        long expiryTimestamp = System.currentTimeMillis() + remainingTimeMs;
        blacklist.put(token, expiryTimestamp);
    }

    public boolean isTokenBlacklisted(String token) {
        return blacklist.containsKey(token) && blacklist.get(token) > System.currentTimeMillis();
    }

    public long getRemainingExpirationTime(String token) {
        // Lógica para leer los Claims del JWT y restar (ExpirationTime - CurrentTime)
        // Por simplicidad, si expira en 1 hora por defecto, se puede retornar 3600000ms
        return 3600000;
    }

    // Tarea programada: Limpia la memoria cada hora de los tokens que ya expiraron por sí solos
    @Scheduled(fixedRate = 3600000)
    public void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        blacklist.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}