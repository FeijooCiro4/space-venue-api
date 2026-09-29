package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.IdNotFoundException;
import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.model.CancellationPolicies;
import com.utn.space.venueaapi.model.EPolicyType;
import com.utn.space.venueaapi.repository.CancellationPoliciesRepository;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class CancellationPoliciesService {
    @Autowired
    CancellationPoliciesRepository cancellationPoliciesRepository;

    public CancellationPolicies findById(Integer id){
        return cancellationPoliciesRepository.findById(id).orElseThrow(()->new IdNotFoundException("Politicas de Cancelacion ", id));
    }

    public CancellationPolicies findByType(EPolicyType tipoEnum){
        return cancellationPoliciesRepository.findByType(tipoEnum).orElseThrow(()->new InvalidDataException("El tipo seleccionado no existe en la base de datos: " + tipoEnum));
    }
}
