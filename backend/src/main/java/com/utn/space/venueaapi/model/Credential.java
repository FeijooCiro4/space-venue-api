package com.utn.space.venueaapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "credentials")
@Entity
public class Credential implements UserDetails {
    @Id
    @NotBlank
    @Schema(description = "UserName único del Usuario", example = "Carlos25")
    private String username;

    @Column(name = "isActive", nullable = false)
    @NotNull
    private Boolean isActive = true;

    // Forzamos a Jackson a que mapee este campo bajo este nombre exacto, vaya a donde vaya
    @JsonProperty(value = "password", access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "password")
    @NotBlank
    @Schema(description = "Contraceña del Usuario", example = "Carlos2000")
    private String password;

    @Enumerated(EnumType.STRING)
    private ERoles rol = ERoles.ROLE_CLIENT;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(rol.name()));
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.isActive;
    }

}