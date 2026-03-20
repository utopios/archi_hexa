package com.bibliotech.infrastructure.adapter.out.persistence;

import com.bibliotech.infrastructure.adapter.out.persistence.entity.MemberJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SpringDataMemberRepository extends JpaRepository<MemberJpaEntity, String> {
}
