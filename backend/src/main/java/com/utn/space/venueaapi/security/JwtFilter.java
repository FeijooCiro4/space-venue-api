package com.utn.space.venueaapi.security;

import com.utn.space.venueaapi.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/// OncePerRequestFilter garantiza que este filtro se ejecute exactamente una vez por cada petición HTTP
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklistService;
    private final UserDetailsService userDetailsService;
    private final SecurityErrorResponder errorResponder;


    // Constructor que permite inyectar el utilitario de manejo de tokens
    public JwtFilter(JwtUtil jwtUtil, TokenBlacklistService blacklistService, UserDetailsService userDetailsService, SecurityErrorResponder errorResponder) {
        this.jwtUtil = jwtUtil;
        this.blacklistService = blacklistService;
        this.userDetailsService = userDetailsService;
        this.errorResponder = errorResponder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Lee el contenido de la cabecera HTTP estándar llamada "Authorization"
        String cabeceraAuth = request.getHeader("Authorization");
        String username = null;
        String jwt = null;

        //Para evitar errores con JWT
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            filterChain.doFilter(request, response);
            return;
        }

        // Comprueba que la cabecera exista y que cumpla con el prefijo internacional de tokens "Bearer "
        if (cabeceraAuth != null && cabeceraAuth.startsWith("Bearer ")) {
            jwt = cabeceraAuth.substring(7); // Corta la cadena para aislar el token puro (remueve "Bearer ")
            try {
                username = jwtUtil.extraerUsername(jwt); // Intenta descifrar el username del token
            } catch (Exception e) {
                logger.warn("Token JWT inválido o expirado: " + e.getMessage());
                errorResponder.escribir(response, HttpStatus.UNAUTHORIZED, "La sesión expiró o el token es inválido.");
                return;
            }
        }


        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            boolean esValido = jwtUtil.validarToken(jwt, username);
            boolean estaEnBlacklist = blacklistService.isTokenBlacklisted(jwt);

            if (esValido && !estaEnBlacklist) {
                // Consultar el estado actual: un JWT anterior no debe eludir la baja de la cuenta.
                // Los permisos tambien se toman de la base y no del claim "rol" del token: si un admin
                // cambia el rol de un usuario (por ej. de OFFERER a CLIENT) el cambio aplica en el
                // siguiente request, sin esperar a que el token expire.
                org.springframework.security.core.userdetails.UserDetails userDetails;
                try {
                    userDetails = userDetailsService.loadUserByUsername(username);
                    if (!userDetails.isEnabled()) {
                        errorResponder.escribir(response, HttpStatus.UNAUTHORIZED, "Su cuenta ya no está activa. Inicie sesión nuevamente.");
                        return;
                    }
                } catch (UsernameNotFoundException e) {
                    errorResponder.escribir(response, HttpStatus.UNAUTHORIZED, "Su cuenta ya no está activa. Inicie sesión nuevamente.");
                    return;
                }

                var authorities = userDetails.getAuthorities();
                String rol = authorities.toString();

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);

                authToken.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
                logger.info("🟢 JWT Válido. Usuario autenticado localmente: '" + username + "' con rol: " + rol);

            } else {
                if (estaEnBlacklist) {
                    logger.warn("Intento de acceso denegado: El token de '" + username + "' está en la Blacklist (Salió por Logout).");
                    errorResponder.escribir(response, HttpStatus.UNAUTHORIZED, "Token invalidado por Logout.");
                    return;
                }
                if (!esValido) {
                    logger.error("¡FALLÓ LA VALIDACIÓN DEL JWT! El jwtUtil determinó que el token para '" + username + "' NO es válido en este entorno local.");
                    errorResponder.escribir(response, HttpStatus.UNAUTHORIZED, "La sesión expiró o el token es inválido.");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}

