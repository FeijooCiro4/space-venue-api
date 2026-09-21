package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.ServiceSelected;
import com.utn.space.venueaapi.model.records.ServiceSelectedDTO;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceSelectedRepository extends JpaRepository<ServiceSelected,Integer> {
    //Este metodo hace una lista de todos los servicios que fueron contratados con una reserva
    @Query("SELECT new com.utn.space.venueaapi.model.records.ServiceSelectedDTO(ss.id,ss.priceAtReservation,ss.descriptionFrozen, ss.reservation.id)" +
            "FROM ServiceSelected ss " +
            "WHERE ss.reservation.id = :idReservation")
    List<ServiceSelectedDTO> findServiceSelectedByIdReservation(@Param("idReservation") Integer idReservation);

    @Modifying
    @Transactional
    @Query("DELETE FROM ServiceSelected s WHERE s.reservation.id = :idReservation")
    void deleteSelectedServiceByReserveId (@Param("idReservation")Integer id);
}
