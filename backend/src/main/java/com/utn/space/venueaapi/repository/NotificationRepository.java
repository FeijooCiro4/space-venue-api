package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification,Integer> {
    List<Notification> findByConsumer_IdConsumer(Integer idConsumer);
}
