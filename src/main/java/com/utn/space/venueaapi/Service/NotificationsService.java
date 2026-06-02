package com.utn.space.venueaapi.Service;

import com.utn.space.venueaapi.Exceptions.ExceptionIdNotFound;
import com.utn.space.venueaapi.Model.Notifications;
import com.utn.space.venueaapi.Repository.NotificationsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class NotificationsService {
    @Autowired
    private NotificationsRepository notificationsRepository;

    /// ---------------------------------Metodos------------------------------------------

    public List<Notifications> listAll(){
        return notificationsRepository.findAll();
    }

    public Notifications findById(Long id){
        return notificationsRepository.findById(id).orElseThrow(()->new ExceptionIdNotFound("Notificacion",id));
    }
}
