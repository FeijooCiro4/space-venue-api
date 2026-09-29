package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.model.Credential;
import com.utn.space.venueaapi.model.ERoles;
import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.repository.CredentialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CredentialService {

    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;


    @Autowired
    public CredentialService(CredentialRepository credentialRepository, PasswordEncoder passwordEncoder) {
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean existsByUsername(String username) {
        return credentialRepository.existsByUsername(username);
    }

    /** Construye una credencial nueva desde una contraseña sin codificar; el registro la persiste en cascada. */
    public Credential createCredential(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            throw new InvalidDataException("Faltan los datos de usuario o contraseña.");
        }
        Credential credential = new Credential();
        credential.setUsername(username);
        credential.setPassword(passwordEncoder.encode(rawPassword));
        credential.setIsActive(true);
        credential.setRol(ERoles.ROLE_CLIENT);
        return credential;
    }

    public List<Credential> findAll() {
        return credentialRepository.findAll();
    }
}
