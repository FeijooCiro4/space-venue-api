package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.Consumer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsumerRepository extends JpaRepository<Consumer,Integer> {
    @Query("SELECT c FROM Consumer c WHERE c.credentials.username = :username")
    Optional<Consumer> findByUsername(@Param("username") String username);

   @Query("SELECT c FROM Consumer c WHERE " +
       "(:firstname IS NULL OR LOWER(c.firstname) LIKE LOWER(CONCAT('%', :firstname, '%'))) AND " +
       "(:lastname IS NULL OR LOWER(c.lastname) LIKE LOWER(CONCAT('%', :lastname, '%'))) AND " +
       "(:email IS NULL OR LOWER(c.email) LIKE LOWER(CONCAT('%', :email, '%'))) AND " +
       "(:phone IS NULL OR c.phone = :phone)")
   List<Consumer> findAllByFilters(@Param("firstname") String firstname,
                                   @Param("lastname") String lastname,
                                   @Param("email") String email,
                                   @Param("phone") String phone);

    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
}
