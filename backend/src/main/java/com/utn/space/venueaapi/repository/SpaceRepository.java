package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.LocationFloorCandidate;
import com.utn.space.venueaapi.model.Space;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpaceRepository extends JpaRepository<Space, Integer> {
    // Misma regla que Space.isBookable(): espacio activo y dueño con cuenta activa y rol OFFERER o ADMIN
    String BOOKABLE = "s.isActive = true AND s.consumerOwner.credentials.isActive = true " +
            "AND s.consumerOwner.credentials.rol IN (com.utn.space.venueaapi.model.ERoles.ROLE_OFFERER, com.utn.space.venueaapi.model.ERoles.ROLE_ADMIN)";

    // Indica si el usuario tiene espacios que no fueron eliminados por él.
    // Se usa para no quitarle el rol OFFERER a alguien que todavía gestiona espacios
    boolean existsByConsumerOwnerIdConsumerAndDeletedByOwnerFalse(Integer idConsumer);

    // Candidatos dentro de una caja geográfica; la distancia exacta se comprueba en el servicio.
    @Query("""
            SELECT s.floor AS floor,
                   s.location.latitude AS latitude,
                   s.location.longitude AS longitude
            FROM Space s
            WHERE s.location.latitude BETWEEN :minLatitude AND :maxLatitude
              AND (
                    (:crossesAntimeridian = false AND s.location.longitude BETWEEN :minLongitude AND :maxLongitude)
                    OR
                    (:crossesAntimeridian = true AND
                        (s.location.longitude >= :minLongitude OR s.location.longitude <= :maxLongitude))
              )
              AND (:excludedSpaceId IS NULL OR s.idSpace <> :excludedSpaceId)
              AND (s.deletedByOwner = false OR s.deletedByOwner IS NULL)
            """)
    List<LocationFloorCandidate> findLocationFloorCandidates(
            @Param("minLatitude") BigDecimal minLatitude,
            @Param("maxLatitude") BigDecimal maxLatitude,
            @Param("minLongitude") BigDecimal minLongitude,
            @Param("maxLongitude") BigDecimal maxLongitude,
            @Param("crossesAntimeridian") boolean crossesAntimeridian,
            @Param("excludedSpaceId") Integer excludedSpaceId
    );

    // Todos los espacios de un dueño, en cualquier estado
    List<Space> findAllByConsumerOwnerIdConsumer(Integer idConsumer);

    // Bloquear el espacio también funciona cuando aún no tiene ninguna reserva.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Space s WHERE s.idSpace = :id")
    Optional<Space> findByIdForUpdate(@Param("id") Integer id);

    @Query("SELECT s FROM Space s WHERE " +
            "( :nameSpace IS NULL OR LOWER(s.nameSpace) LIKE LOWER(CONCAT('%', :nameSpace, '%')) ) AND " +
            "( :minPrice IS NULL OR s.basePrice >= :minPrice ) AND " +
            "( :maxPrice IS NULL OR s.basePrice <= :maxPrice ) AND " +
            "( :idConsumerOwner IS NULL OR s.consumerOwner.idConsumer = :idConsumerOwner ) AND " +
            "( :idLocation IS NULL OR s.location.idLocation = :idLocation ) AND " +
            BOOKABLE //Este metodo solo busca espacios publicados (activos y con el dueño disponible)

    )
    List<Space> findAllByFields(
            @Param("idConsumerOwner") Integer id_consumer_owner,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("nameSpace") String name_space, // <- Corregido a String
            @Param("idLocation") Integer id_location
    );

    @Query("SELECT s FROM Space s WHERE " +
            "( :nameSpace IS NULL OR LOWER(s.nameSpace) LIKE LOWER(CONCAT('%', :nameSpace, '%')) ) AND " +
            "( :minPrice IS NULL OR s.basePrice >= :minPrice ) AND " +
            "( :maxPrice IS NULL OR s.basePrice <= :maxPrice ) AND " +
            "( :idConsumerOwner IS NULL OR s.consumerOwner.idConsumer = :idConsumerOwner ) AND " +
            "( :idLocation IS NULL OR s.location.idLocation = :idLocation )" //Este metodo no filtra los inactivos
    )
    List<Space> findAllByFieldsWithInactives(
            @Param("idConsumerOwner") Integer id_consumer_owner,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice,
            @Param("nameSpace") String name_space,
            @Param("idLocation") Integer id_location
    );


    @Query("SELECT s FROM Space s WHERE " + BOOKABLE)
    List<Space>findAllWithOutInactives();


    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Space u SET u.rating = :rating, u.commentCount = :commentCount WHERE u.idSpace = :id")
    void updateRatingAndCount(
            @Param("id") Integer spaceId,
            @Param("rating") double rating,
            @Param("commentCount") Integer commentCount
    );

    @Query("SELECT COUNT(s) > 0 FROM Space s WHERE s.idSpace = :idSpace AND " + BOOKABLE)
    boolean existsBookableById(@Param("idSpace") Integer idSpace);
}
