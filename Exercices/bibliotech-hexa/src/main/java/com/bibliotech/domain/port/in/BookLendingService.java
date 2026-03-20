package com.bibliotech.domain.port.in;

import com.bibliotech.domain.model.BorrowingRecord;
import com.bibliotech.domain.model.Member;

public interface BookLendingService {
    BorrowingRecord borrowBook(String memberId, String isbn);
    BorrowingRecord returnBook(Long borrowingId);
    void payPenalty(String memberId, double amount);
    Member registerMember(String name, String email);
    Member findMember(String memberId);
    java.util.List<Member> findAllMembers();
}
