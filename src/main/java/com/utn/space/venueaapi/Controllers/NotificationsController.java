package com.utn.space.venueaapi.Controllers;

import com.utn.space.venueaapi.Exceptions.ExceptionIdNotFound;
import com.utn.space.venueaapi.Model.Notifications;
import com.utn.space.venueaapi.Service.NotificationsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/s&v")
public class NotificationsController {
    @Autowired
    private NotificationsService notificationService;

    /// -------------------
    @GetMapping
    public List<Notifications> listAll (){
        return notificationService.listAll();
    }

    @GetMapping("/{id}")
    public Notifications findForId (@PathVariable Long id){
        return notificationService.findById(id);
    }
    @ExceptionHandler(ExceptionIdNotFound.class)
    public ResponseEntity<String> idNotFound (ExceptionIdNotFound e){
        return ResponseEntity<String> responce = ResponseEntity.notFound(HttpStatus.NOT_FOUND,body(e.getMessage()));
    }

}
