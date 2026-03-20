package com.utopios.module3.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository Spring Data JPA pour les entités OrderJpaEntity.
 * Interface technique, ne fait pas partie du domaine.
 */
@Repository
public interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, String> {

    List<OrderJpaEntity> findByCustomerId(String customerId);
}
