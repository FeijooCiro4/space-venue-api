package com.utn.space.venueaapi.model.records;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CredentialRegistrationDTO(@NotBlank String username, @NotBlank String password) {}
