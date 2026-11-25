package com.utn.space.venueaapi.repository;

import com.utn.space.venueaapi.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository

public interface CommentRepository extends JpaRepository<Comment,Integer> {

    // Se utiliza la anotación Query porque en las convenciones de Spring Data JPA
    // el carácter de guion bajo (_) no se interpreta como un carácter de texto plano
    @Query("SELECT c FROM Comment c WHERE c.space.idSpace = :idSpace AND c.isActive = true")
    List<Comment> findAllBySpaceIdSpace(@Param("idSpace") Integer idSpace);

    //Solo comentarios activos de espacios activos (lo que puede ver cualquier usuario que no sea admin)
    @Query("SELECT c FROM Comment c WHERE c.consumer.idConsumer = :idConsumer AND c.space.isActive AND c.isActive = true")
    List<Comment> findAllByConsumerIdConsumer(@Param("idConsumer") Integer idConsumer);

    //Todos los comentarios del usuario, también los de espacios desactivados (vista de admin)
    @Query("SELECT c FROM Comment c WHERE c.consumer.idConsumer = :idConsumer")
    List<Comment> findAllByConsumerIncludingInactiveSpaces(@Param("idConsumer") Integer idConsumer);

    List<Comment> findAllByOrderByScoreAsc();
    List<Comment> findAllByOrderByScoreDesc();
    // Un comentario cuenta para la regla de un comentario por espacio si esta activo o si lo desactivo
    // un admin. Los que elimino el propio autor no cuentan puede volver a comentar.
    @Query("""
            SELECT COUNT(c) > 0 FROM Comment c
            WHERE c.consumer.idConsumer = :consumerId AND c.space.idSpace = :spaceId
              AND (c.isActive = true
                   OR c.deactivationReason IS NULL 
                   OR c.deactivationReason <> com.utn.space.venueaapi.model.ECommentDeactivationReason.AUTHOR_REQUEST)
            """)
    boolean existsBlockingCommentOnSpace(
            @Param("consumerId") Integer consumerId,
            @Param("spaceId") Integer spaceId
    );

    @Query("""
            SELECT COUNT(c) > 0 FROM Comment c
            WHERE c.consumer.idConsumer = :consumerId AND c.space.idSpace = :spaceId AND c.isActive = true
            """)
    boolean existsActiveCommentOnSpace(
            @Param("consumerId") Integer consumerId,
            @Param("spaceId") Integer spaceId
    );


    @Query("SELECT c FROM Comment c WHERE c.isActive = true")
    List<Comment> findAllActive();

    @Query("SELECT c FROM Comment c WHERE c.idComment = :id AND c.isActive = true")
    Optional<Comment> findActiveById(@Param("id") Integer id);

    @Query("SELECT c FROM Comment c WHERE c.isActive = true ORDER BY c.score ASC")
    List<Comment> findAllActiveOrderByScoreAsc();

    @Query("SELECT c FROM Comment c WHERE c.isActive = true ORDER BY c.score DESC")
    List<Comment> findAllActiveOrderByScoreDesc();

    @Query("SELECT c FROM Comment c WHERE c.consumer.idConsumer = :idConsumer AND c.isActive = false")
    List<Comment> findDeactivatedCommentsForConsumer(@Param("idConsumer") Integer idConsumer);

    @Query("SELECT c FROM Comment c WHERE c.space.idSpace = :idSpace AND c.isActive = false")
    List<Comment> findDeactivatedCommentsForSpace(@Param("idSpace") Integer idSpace);
}