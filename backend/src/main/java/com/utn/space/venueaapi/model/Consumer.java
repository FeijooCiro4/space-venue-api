package com.utn.space.venueaapi.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "consumers")
@Entity
public class Consumer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idConsumer;
    private String firstname;
    private String lastname;

    @Email
    private String email;
    private String phone;

    // Al usar CascadeType.ALL, Hibernate persiste la Credencial
    // en la base de datos automáticamente al guardar el Consumer.
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "username")
    private Credential credentials;

    // Fecha (UTC) de la última solicitud para ser oferente. Se usa para no permitir
    // más de una solicitud cada 24 horas. null si nunca pidió serlo.
    @JsonIgnore
    private LocalDateTime offererRequestedAt;

}