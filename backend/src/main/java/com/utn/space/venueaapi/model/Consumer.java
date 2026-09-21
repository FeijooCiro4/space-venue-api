package com.utn.space.venueaapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

}