package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.Notification;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification,Integer> {
    List<Notification> findByConsumer_IdConsumer(Integer idConsumer);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isSeen = true WHERE n.consumer.idConsumer = :idConsumer AND n.isSeen = false")
    int markAllAsSeenByConsumerId(@Param("idConsumer") Integer idConsumer);
}
