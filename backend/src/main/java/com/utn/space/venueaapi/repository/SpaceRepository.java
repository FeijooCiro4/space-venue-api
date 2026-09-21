package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.Space;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpaceRepository extends JpaRepository<Space, Integer> {
    @Query("SELECT s FROM Space s WHERE " +
            "( :nameSpace IS NULL OR LOWER(s.nameSpace) LIKE LOWER(CONCAT('%', :nameSpace, '%')) ) AND " +
            "( :minPrice IS NULL OR s.basePrice >= :minPrice ) AND " +
            "( :maxPrice IS NULL OR s.basePrice <= :maxPrice ) AND " +
            "( :idConsumerOwner IS NULL OR s.consumerOwner.idConsumer = :idConsumerOwner ) AND " +
            "( :idLocation IS NULL OR s.location.idLocation = :idLocation ) AND " +
            "( s.isActive)" //Este metodo solo busca espacios activos

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


    @Query("SELECT s FROM Space s WHERE s.isActive")
    List<Space>findAllWithOutInactives();

    boolean existsByIdSpaceAndIsActiveTrue(Integer idSpace);
}
