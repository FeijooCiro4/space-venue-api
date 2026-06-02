package com.utn.space.venueaapi.Service;

import com.utn.space.venueaapi.Exceptions.ExceptionIdNotFound;
import com.utn.space.venueaapi.Model.Reservations;
import com.utn.space.venueaapi.Repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReservationService {
    @Autowired
    private ReservationRepository reservationRepository;

    /// -------------------Metodos-------------------------------------------

    public List<Reservations> findAll (){
        return reservationRepository.findAll();
    }

    public Reservations findById (Long id){
        return reservationRepository.findById(id).orElseThrow(()-> new ExceptionIdNotFound("Reservacion",id));
    }
}
