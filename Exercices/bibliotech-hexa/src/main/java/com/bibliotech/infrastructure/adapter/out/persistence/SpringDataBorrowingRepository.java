package com.bibliotech.infrastructure.adapter.out.persistence;

import com.bibliotech.infrastructure.adapter.out.persistence.entity.BorrowingRecordJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataBorrowingRepository extends JpaRepository<BorrowingRecordJpaEntity, Long> {
    List<BorrowingRecordJpaEntity> findByMemberIdAndReturnedFalse(String memberId);
    List<BorrowingRecordJpaEntity> findByIsbnAndReturnedFalse(String isbn);
}
