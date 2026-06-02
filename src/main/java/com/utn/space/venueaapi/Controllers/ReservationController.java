package com.utn.space.venueaapi.Controllers;

import com.utn.space.venueaapi.Exceptions.ExceptionIdNotFound;
import com.utn.space.venueaapi.Model.Reservations;
import com.utn.space.venueaapi.Service.ReservationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/s&v/Reservaciones")
public class ReservationController {
    @Autowired
    private ReservationService reservationsService;

    /// ---------------------Metodos-----------------------------------------------------

    @GetMapping
    public List<Reservations> findAll (){
        return reservationsService.findAll();
    }

    @GetMapping("/{id}")
    public Reservations findById (@PathVariable Long id){
        return reservationsService.findById(id);
    }

    @ExceptionHandler(ExceptionIdNotFound.class)
    public ResponseEntity<String> idNotFound (ExceptionIdNotFound e){
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(e.getMessage());
    }
    /*
    @PostMapping
    public Reservations createReservation(@RequestBody )
    */
}
