package com.utn.space.venueaapi.Service;

import com.utn.space.venueaapi.model.Credentials;
import com.utn.space.venueaapi.Repository.CredentialsRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Data
public class CustomUserDetailsService implements UserDetailsService {
    private final CredentialsRepository credentialsRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. Buscamos NUESTRO usuario en la base de datos usando el repositorio
        Credentials currentUser = credentialsRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        // 2. Traducimos NUESTRO usuario al "User" que entiende Spring Security
        return User.withUsername(currentUser.getUsername())
                // La contraseña ya debe estar encriptada en la base de datos con BCrypt
                .password(currentUser.getPasswordHash())
                // Le pasamos el rol que trajimos de la BD
                .roles(currentUser.getRol().toString())
                .build();
    }

}
