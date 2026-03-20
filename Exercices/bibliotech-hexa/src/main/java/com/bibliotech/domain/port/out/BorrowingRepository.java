package com.bibliotech.domain.port.out;

import com.bibliotech.domain.model.BorrowingRecord;
import com.bibliotech.domain.vo.ISBN;
import com.bibliotech.domain.vo.MemberId;
import java.util.List;
import java.util.Optional;

public interface BorrowingRepository {
    Optional<BorrowingRecord> findById(Long id);
    List<BorrowingRecord> findActiveByMember(MemberId memberId);
    List<BorrowingRecord> findActiveByBook(ISBN isbn);
    BorrowingRecord save(BorrowingRecord record);
}
