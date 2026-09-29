package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.SpaceImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpaceImageRepository extends JpaRepository<SpaceImage,Integer> {
    @Query("SELECT si FROM SpaceImage si WHERE si.space.idSpace = :idSpace")
    List<SpaceImage> findAllBySpaceIdSpace(@Param("idSpace") Integer idSpace);
}
