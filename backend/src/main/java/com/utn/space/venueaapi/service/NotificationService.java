package com.utn.space.venueaapi.service;

import com.utn.space.venueaapi.exceptions.IdNotFoundException;
import com.utn.space.venueaapi.exceptions.InvalidDataException;
import com.utn.space.venueaapi.model.Consumer;
import com.utn.space.venueaapi.model.ENotificationType;
import com.utn.space.venueaapi.model.ERoles;
import com.utn.space.venueaapi.model.Notification;
import com.utn.space.venueaapi.repository.ConsumerRepository;
import com.utn.space.venueaapi.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class NotificationService {
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ConsumerService consumerService;
    @Autowired
    private ConsumerRepository consumerRepository;
    @Autowired
    private NotificationStreamService notificationStreamService;

    //Tiempo mínimo entre dos solicitudes para ser oferente del mismo usuario
    private static final long HORAS_ENTRE_SOLICITUDES_OFERENTE = 24;


    ///--------------------------------Metodos-------------------------------------------------------------
    ///
    public List<Notification> listAll(){
        return notificationRepository.findAll();
    }

    public Notification findById(Integer id){
        return notificationRepository.findById(id).orElseThrow(()->new IdNotFoundException("la notificación",id));
    }

    public Notification markAsSeen (Integer id){
        Notification aux= notificationRepository.findById(id).orElseThrow(()->new IdNotFoundException("la notificación",id));
        aux.setIsSeen(true);
        Notification saved = notificationRepository.save(aux);
        notificationStreamService.publishUnreadCount(saved.getConsumer().getIdConsumer());
        return saved;
    }

    public int markAllAsSeenForLoggedConsumer() {
        Integer consumerId = consumerService.getLoggedConsumerId();
        int updatedCount = notificationRepository.markAllAsSeenByConsumerId(consumerId);
        notificationStreamService.publishUnreadCount(consumerId);
        return updatedCount;
    }

    public List<Notification> listAllByIdConsumer (Integer id){
        return notificationRepository.findByConsumer_IdConsumer(id);
    }

    public List<Notification> listAllByIdConsumerForConsumer(){
        List<Notification> notificationList = notificationRepository.findByConsumer_IdConsumer(consumerService.getLoggedConsumerId());

        /*for (Notification notification : notificationList){
            if(!notification.getIsSeen()){
                markAsSeen(notification.getIdNotification()); //Marco cada una de las notificaciones mostradas como vistas
            }
        }
        Se marca como comentada ya que no tiene una funcion util
        */ 
        return notificationList;
    }

    public List<Notification> listAllUnseenForConsumer(){
        List<Notification> notificationList = notificationRepository.findByConsumer_IdConsumer(consumerService.getLoggedConsumerId());

        //filtro las notificacion ya vistas.
        notificationList = notificationList.stream().filter(notification -> !notification.getIsSeen()).toList();

        /*for (Notification notification : notificationList){
            markAsSeen(notification.getIdNotification()); //Marco cada una de las notificaciones mostradas como vistas
        }
        Se marca como comentada ya que no tiene una funcion util
        */
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
        return createNotification(consumer, message, null, null);
    }

    public Notification createNotification(Consumer consumer, String message, ENotificationType type, Integer referenceId) {
        Notification notification = new Notification();
        notification.setConsumer(consumer);
        notification.setMessage(message);
        notification.setType(type);
        notification.setReferenceId(referenceId);
        // Guardamos la fecha en UTC para evitar desfasajes entre servidor/cliente
        notification.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        notification.setIsSeen(false);
        Notification saved = notificationRepository.save(notification);
        notificationStreamService.publishNewNotification(saved);
        return saved;
    }

    //El usuario logueado pide ser oferente: se manda una notificación a cada admin activo
    //con sus datos de contacto. La verificación la hace el admin por mail y, si corresponde,
    //le cambia el rol desde la gestión de usuarios. Devuelve a cuántos admins se notificó.
    @Transactional
    public int requestOffererRole() {
        Consumer consumer = consumerService.findById(consumerService.getLoggedConsumerId());

        if (consumer.getCredentials() == null || consumer.getCredentials().getRol() != ERoles.ROLE_CLIENT) {
            throw new InvalidDataException("Solo un cliente puede solicitar ser oferente.");
        }

        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime ultima = consumer.getOffererRequestedAt();
        if (ultima != null && ultima.plusHours(HORAS_ENTRE_SOLICITUDES_OFERENTE).isAfter(ahora)) {
            throw new InvalidDataException("Ya enviaste una solicitud para ser oferente. Podés volver a intentarlo pasadas 24 horas desde la última.");
        }

        List<Consumer> admins = consumerRepository.findAllActiveByRol(ERoles.ROLE_ADMIN);
        if (admins.isEmpty()) {
            throw new InvalidDataException("No hay administradores disponibles para recibir la solicitud.");
        }

        String message = String.format(
                "El usuario %s (%s %s) solicita ser oferente. Contactalo por mail a %s para verificar sus datos y, si corresponde, cambiale el rol a Oferente desde la gestión de usuarios.",
                consumer.getCredentials().getUsername(),
                consumer.getFirstname(),
                consumer.getLastname(),
                consumer.getEmail()
        );
        admins.forEach(admin -> createNotification(admin, message, ENotificationType.OFFERER_REQUEST, null));

        consumer.setOffererRequestedAt(ahora);
        consumerRepository.save(consumer);
        return admins.size();
    }
}
