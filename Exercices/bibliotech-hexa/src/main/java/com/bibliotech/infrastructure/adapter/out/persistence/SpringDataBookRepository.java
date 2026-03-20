package com.bibliotech.infrastructure.adapter.out.persistence;

import com.bibliotech.infrastructure.adapter.out.persistence.entity.BookJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataBookRepository extends JpaRepository<BookJpaEntity, String> {
    List<BookJpaEntity> findByTitleContainingIgnoreCase(String keyword);
}
