package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.model.Credential;
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

    //Registra o actualiza una credencial aplicando algoritmos de hashing delegados
    public void saveCredential(Credential credential) {
        if (credential.getPassword() == null || credential.getPassword().isEmpty()) {
            throw new IllegalArgumentException("La contraseña de la credencial no puede ser nula o vacía.");
        }

        String securedPassword = passwordEncoder.encode(credential.getPassword());
        credential.setPassword(securedPassword);

        credential.setIsActive(true);

        credentialRepository.save(credential);
    }

    public List<Credential> findAll() {
        return credentialRepository.findAll();
    }
}
