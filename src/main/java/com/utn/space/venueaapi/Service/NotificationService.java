package com.utn.space.venueaapi.Service;

import com.utn.space.venueaapi.Exceptions.ExceptionIdNotFound;
import com.utn.space.venueaapi.model.Notifications;
import com.utn.space.venueaapi.Repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {
    @Autowired
    private NotificationRepository notificationsRepository;

    /// ---------------------------------Metodos------------------------------------------

    public List<Notifications> listAll(){
        return notificationsRepository.findAll();
    }

    public Notifications findById(Long id){
        return notificationsRepository.findById(id).orElseThrow(()->new ExceptionIdNotFound("Notificacion",id));
    }

    public void markAsSeen (Long id){
        Notifications noti= notificationsRepository.findById(id).orElseThrow(()->new ExceptionIdNotFound("Notificacion",id));
        noti.setVisto(true);
        notificationsRepository.save(noti);
    }
}
