package com.utn.space.venueaapi.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.SecretKey;
import java.time.Clock;
import java.util.UUID;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey CLAVE_SECRETA;
    private final Clock clock;

    @Autowired
    public JwtUtil(@Value("${app.jwt.secret-base64}") String secretBase64) {
        this(secretBase64, Clock.systemUTC());
    }

    public JwtUtil(String secretBase64, Clock clock) {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalArgumentException("Configure JWT_SECRET_BASE64 con al menos 32 bytes aleatorios codificados en Base64.");
        }
        this.CLAVE_SECRETA = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
        this.clock = clock;
    }

    // Define la vida útil del token en 10 horas expresadas en milisegundos
    private final long TIEMPO_EXPIRACION = 36_000_000;

    // Construye el token JWT empaquetando el nombre de usuario del cliente
    public String generarToken(String username, String rol) {
        return Jwts.builder()
                .setSubject(username)
                .setId(UUID.randomUUID().toString())
                .claim("rol", rol) // Para poder pasar roles en el payload
                .setIssuedAt(new Date(clock.millis()))
                .setExpiration(new Date(clock.millis() + TIEMPO_EXPIRACION))
                .signWith(CLAVE_SECRETA, Jwts.SIG.HS256)
                .compact();
    }

    // Sobrecarga para incluir consumerId en el token
    public String generarToken(String username, String rol, Integer consumerId) {
        return Jwts.builder()
                .setSubject(username)
                .setId(UUID.randomUUID().toString())
                .claim("rol", rol)
                .claim("consumerId", consumerId)  // Agrega el consumerId al payload
                .setIssuedAt(new Date(clock.millis()))
                .setExpiration(new Date(clock.millis() + TIEMPO_EXPIRACION))
                .signWith(CLAVE_SECRETA, Jwts.SIG.HS256)
                .compact();
    }

    // Abre el token y extrae su payload de datos (Claims) usando la firma secreta de control
    public Claims extraerClaims(String token) {
        return Jwts.parser()
                .clock(() -> Date.from(clock.instant()))
                .setSigningKey(CLAVE_SECRETA) // Suministra la clave para comprobar que el token no se modificó en el camino
                .build()
                .parseClaimsJws(token) // Intenta parsear e inspeccionar la firma del token
                .getBody(); // Devuelve el mapa interno de datos si la firma es válida
    }

    // Recupera directamente el nombre de usuario desde el cuerpo del token
    public String extraerUsername(String token) {
        return extraerClaims(token).getSubject(); // El Subject almacena el username
    }

    // Metodo para recuperar el rol
    public String extraerRol(String token) {
        return extraerClaims(token).get("rol", String.class);
    }

    // Metodo para recuperar el consumerId
    public Integer extraerConsumerId(String token) {
        Object consumerId = extraerClaims(token).get("consumerId");
        if (consumerId instanceof Integer) {
            return (Integer) consumerId;
        } else if (consumerId instanceof Long) {
            return ((Long) consumerId).intValue();
        }
        return null;
    }

    // Verifica que el token pertenezca al usuario en cuestión y que la fecha actual no supere la de expiración
    public boolean validarToken(String token, String username) {
        final String tokenUsername = extraerUsername(token);
        boolean estaExpirado = extraerClaims(token).getExpiration().before(Date.from(clock.instant()));
        return (tokenUsername.equals(username) && !estaExpirado); // Retorna verdadero solo si ambas condiciones se cumplen
    }
}

