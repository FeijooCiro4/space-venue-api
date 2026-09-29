package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.model.Consumer;
import com.utn.space.venueaapi.model.records.RegistroDTO;
import com.utn.space.venueaapi.repository.ConsumerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationService {
    private final CredentialService credentialService;
    private final ConsumerRepository consumerRepository;

    @Transactional
    public Consumer register(RegistroDTO dto) {
        if (dto.username() == null || dto.username().isBlank() || dto.password() == null || dto.password().isBlank()) {
            throw new InvalidDataException("Faltan los datos de usuario o contraseña.");
        }
        if (credentialService.existsByUsername(dto.username())) {
            throw new InvalidDataException("El nombre de usuario ya está en uso.");
        }
        Consumer consumer = new Consumer();
        consumer.setFirstname(dto.firstname());
        consumer.setLastname(dto.lastname());
        consumer.setEmail(dto.email());
        consumer.setPhone(dto.phone());
        consumer.setCredentials(credentialService.createCredential(dto.username(), dto.password()));
        try {
            // Consumer nuevo: persist (no merge) propaga INSERT a Credential. Una carrera de
            // registros no puede sobrescribir la contraseña de una cuenta existente.
            return consumerRepository.saveAndFlush(consumer);
        } catch (DataIntegrityViolationException e) {
            throw new InvalidDataException("No se pudo registrar el usuario: los datos entran en conflicto con un registro existente.");
        }
    }
}
