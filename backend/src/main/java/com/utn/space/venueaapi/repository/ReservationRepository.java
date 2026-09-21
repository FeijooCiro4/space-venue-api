package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation,Integer> {

    // Evita que dos personas puedan reservar el mismo espacio al mismo tiempo
    @Query("SELECT COUNT(r) > 0 FROM Reservation r WHERE r.space.idSpace = :idSpace " +
            "AND r.status != 'CANCELLED' " + // Excluir las canceladas
            "AND (:fromDate < r.untilDate AND :untilDate > r.fromDate)")
    boolean existsOverlappingReservation(@Param("idSpace") Integer idSpace,
                                         @Param("fromDate") LocalDateTime fromDate,
                                         @Param("untilDate") LocalDateTime untilDate);

    List<Reservation> findAllByConsumer_IdConsumer(Integer idConsumer);
    List<Reservation> findAllBySpace_IdSpace(Integer idSpace);

    // Cuenta las reservas completadas o confirmadas de un usuario en un espacio específico
    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.consumer.idConsumer = :idConsumer " +
            "AND r.space.idSpace = :idSpace " +
            "AND (r.status = 'CONFIRMED' OR r.status = 'COMPLETED')")
    long countCompletedReservationsByConsumerAndSpace(@Param("idConsumer") Integer idConsumer,
                                                      @Param("idSpace") Integer idSpace);
}
