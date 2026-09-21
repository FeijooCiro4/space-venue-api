package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.IdNotFoundException;
import com.utn.space.venueaapi.model.Consumer;
import com.utn.space.venueaapi.model.Notification;
import com.utn.space.venueaapi.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class NotificationService {
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ConsumerService consumerService;


    ///--------------------------------Matodos-------------------------------------------------------------
    ///
    public List<Notification> listAll(){
        return notificationRepository.findAll();
    }

    public Notification findById(Integer id){
        return notificationRepository.findById(id).orElseThrow(()->new IdNotFoundException("Notificacion",id));
    }

    public Notification markAsSeen (Integer id){
        Notification aux= notificationRepository.findById(id).orElseThrow(()->new IdNotFoundException("Notificacion",id));
        aux.setIsSeen(true);
        return notificationRepository.save(aux);
    }

    public List<Notification> listAllByIdConsumer (Integer id){
        return notificationRepository.findByConsumer_IdConsumer(id);
    }

    public List<Notification> listAllByIdConsumerForConsumer(){
        List<Notification> notificationList = notificationRepository.findByConsumer_IdConsumer(consumerService.getLoggedConsumerId());

        for (Notification notification : notificationList){
            if(!notification.getIsSeen()){
                markAsSeen(notification.getIdNotification()); //Marco cada una de las notificaciones mostradas como vistas
            }
        }

        return notificationList;
    }

    public List<Notification> listAllUnseenForConsumer(){
        List<Notification> notificationList = notificationRepository.findByConsumer_IdConsumer(consumerService.getLoggedConsumerId());

        //filtro las notificacion ya vistas.
        notificationList = notificationList.stream().filter(notification -> !notification.getIsSeen()).toList();

        for (Notification notification : notificationList){
            markAsSeen(notification.getIdNotification()); //Marco cada una de las notificaciones mostradas como vistas
        }

        return notificationList;
    }

    public long countUnseenForConsumer() {
        List<Notification> notificationList = notificationRepository.findByConsumer_IdConsumer(consumerService.getLoggedConsumerId());

        return notificationList.stream()
                .filter(notification -> !notification.getIsSeen())
                .count();
    }

    // Método para crear una notificación
    public Notification createNotification(Consumer consumer, String message) {
        Notification notification = new Notification();
        notification.setConsumer(consumer);
        notification.setMessage(message);
        // Guardamos la fecha en UTC para evitar desfasajes entre servidor/cliente
        notification.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        notification.setIsSeen(false);
        return notificationRepository.save(notification);
    }
}
