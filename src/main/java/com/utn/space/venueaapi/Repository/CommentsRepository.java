package com.utn.space.venueaapi.Repository;

import com.utn.space.venueaapi.model.Comments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository

public interface CommentsRepository extends JpaRepository<Comments,Long> {
}
