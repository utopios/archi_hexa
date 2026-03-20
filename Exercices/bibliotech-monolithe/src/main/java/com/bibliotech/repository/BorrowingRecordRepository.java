package com.bibliotech.repository;

import com.bibliotech.model.BorrowingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BorrowingRecordRepository extends JpaRepository<BorrowingRecord, Long> {
    List<BorrowingRecord> findByMemberMemberIdAndReturnedFalse(String memberId);
    List<BorrowingRecord> findByBookIsbnAndReturnedFalse(String isbn);
}
