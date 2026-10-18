package com.utn.space.venueaapi.config;

import com.utn.space.venueaapi.security.JwtFilter;
import com.utn.space.venueaapi.security.JwtUtil;
import com.utn.space.venueaapi.security.SecurityErrorResponder;
import com.utn.space.venueaapi.service.TokenBlacklistService;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService blacklistService;
    private final SecurityErrorResponder errorResponder;

    public SecurityConfig(JwtUtil jwtUtil, TokenBlacklistService blacklistService, SecurityErrorResponder errorResponder) {
        this.jwtUtil = jwtUtil;
        this.blacklistService = blacklistService;
        this.errorResponder = errorResponder; //Se usa para formatear los errores JWT en un JSON como los de global exception handler
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource,
                                          UserDetailsService userDetailsService) throws Exception {http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable()) // Desactiva la protección CSRF ya que al usar JWT la API es inherentemente inmune
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Configura la API sin estado, no guarda sesiones en el servidor
                .exceptionHandling(ex -> ex
                        // Sin token en una ruta protegida → 401
                        .authenticationEntryPoint((request, response, authException) ->
                                errorResponder.escribir(response, HttpStatus.UNAUTHORIZED,
                                        "Debe iniciar sesión para acceder a este recurso."))
                        // Autenticado pero sin el rol requerido → 403
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                errorResponder.escribir(response, HttpStatus.FORBIDDEN,
                                        "No tiene permisos para acceder a este recurso."))
                )
                .authorizeHttpRequests(auth -> auth
                        // RUTAS PÚBLICAS (Visitantes sin loguear)
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/spaces/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/spaces/byfields").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/spaceimages/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios").permitAll()
                        .requestMatchers("/api/spaces/search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/comments/byspaceid/**").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/webjars/**",
                                "/swagger-resources/**",
                                "/configuration/ui",
                                "/configuration/security"
                        ).permitAll()

                        // RUTAS EXCLUSIVAS DE ADMINISTRADOR
                        // Todas las rutas que empiecen con /api/admin requieren obligatoriamente ROLE_ADMIN
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // CUALQUIER OTRA RUTA (Exige estar autenticado como CLIENT o ADMIN)
                        .anyRequest().authenticated()
                );

        // Agrega el filtro interceptor de JWT justo antes del filtro nativo de autenticación de Spring por usuario/contraseña
        http.addFilterBefore(new JwtFilter(jwtUtil, blacklistService, userDetailsService, errorResponder), UsernamePasswordAuthenticationFilter.class);

        return http.build(); // Retorna la cadena de configuración construida
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService, // Spring inyectará automáticamente el servicio que implementa la carga de usuarios
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder); // Le obligamos a usar el DelegatingPasswordEncoder
        return authProvider;
    }

    @Bean
    BCryptPasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();//Cambiado a bycriptPassword encoder
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

}
