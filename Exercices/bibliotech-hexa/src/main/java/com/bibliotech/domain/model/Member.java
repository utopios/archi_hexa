package com.bibliotech.domain.model;

import com.bibliotech.domain.exception.BorrowingLimitReachedException;
import com.bibliotech.domain.exception.UnpaidPenaltiesException;
import com.bibliotech.domain.vo.Email;
import com.bibliotech.domain.vo.MemberId;
import com.bibliotech.domain.vo.MemberName;
import com.bibliotech.domain.vo.Penalty;

public class Member {

    private static final int MAX_BORROWED_BOOKS = 3;

    private final MemberId memberId;
    private final MemberName name;
    private final Email email;
    private int borrowedBooksCount;
    private Penalty unpaidPenalties;

    private Member(MemberId memberId, MemberName name, Email email,
                   int borrowedBooksCount, Penalty unpaidPenalties) {
        this.memberId = memberId;
        this.name = name;
        this.email = email;
        this.borrowedBooksCount = borrowedBooksCount;
        this.unpaidPenalties = unpaidPenalties;
    }


    public static Member register(MemberId memberId, MemberName name, Email email) {
        return new Member(memberId, name, email, 0, Penalty.zero());
    }

    public static Member reconstitute(MemberId memberId, MemberName name, Email email,
                                      int borrowedBooksCount, Penalty unpaidPenalties) {
        return new Member(memberId, name, email, borrowedBooksCount, unpaidPenalties);
    }

    public boolean canBorrow() {
        return borrowedBooksCount < MAX_BORROWED_BOOKS && !unpaidPenalties.isUnpaid();
    }

    public void borrow() {
        if (borrowedBooksCount >= MAX_BORROWED_BOOKS) {
            throw new BorrowingLimitReachedException(memberId.value());
        }
        if (unpaidPenalties.isUnpaid()) {
            throw new UnpaidPenaltiesException(memberId.value(), unpaidPenalties.amount());
        }
        this.borrowedBooksCount++;
    }

    public void returnBook() {
        if (this.borrowedBooksCount <= 0) {
            throw new IllegalStateException("Le membre n'a aucun livre emprunte");
        }
        this.borrowedBooksCount--;
    }

    public void addPenalty(Penalty penalty) {
        this.unpaidPenalties = this.unpaidPenalties.add(penalty);
    }

    public void payPenalty(double amount) {
        this.unpaidPenalties = this.unpaidPenalties.subtract(amount);
    }

    public MemberId getMemberId() { return memberId; }
    public MemberName getName() { return name; }
    public Email getEmail() { return email; }
    public int getBorrowedBooksCount() { return borrowedBooksCount; }
    public Penalty getUnpaidPenalties() { return unpaidPenalties; }
}
