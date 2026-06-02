package com.utn.space.venueaapi.Repository;

import com.utn.space.venueaapi.Model.Notifications;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository

public interface NotificationsRepository extends JpaRepository<Notifications,Long> {
}
