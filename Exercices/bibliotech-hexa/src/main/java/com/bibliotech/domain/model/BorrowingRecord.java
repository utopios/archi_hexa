package com.bibliotech.domain.model;

import com.bibliotech.domain.event.*;
import com.bibliotech.domain.exception.BookAlreadyReturnedException;
import com.bibliotech.domain.vo.ISBN;
import com.bibliotech.domain.vo.MemberId;
import com.bibliotech.domain.vo.Penalty;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BorrowingRecord {

    private Long id;
    private final MemberId memberId;
    private final ISBN isbn;
    private final LocalDate borrowDate;
    private LocalDate returnDate;
    private boolean returned;
    private Penalty penalty;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private BorrowingRecord(MemberId memberId, ISBN isbn, LocalDate borrowDate) {

        this.memberId = memberId;
        this.isbn = isbn;
        this.borrowDate = borrowDate;
        this.returned = false;
        this.penalty = Penalty.zero();
    }

    public BorrowingRecord(Long id, MemberId memberId, ISBN isbn, LocalDate borrowDate,
                            LocalDate returnDate, boolean returned, double penaltyAmount) {
        this.id = id;
        this.memberId = memberId;
        this.isbn = isbn;
        this.borrowDate = borrowDate;
        this.returnDate = returnDate;
        this.returned = returned;
        this.penalty = new Penalty(penaltyAmount);
    }

    public static BorrowingRecord create(MemberId memberId, ISBN isbn, LocalDate borrowDate) {
        BorrowingRecord record = new BorrowingRecord(memberId, isbn, borrowDate);
        record.domainEvents.add(new BookBorrowed(memberId, isbn, borrowDate));
        return record;
    }

    public Penalty markAsReturned(LocalDate returnDate) {
        if (this.returned) {
            throw new BookAlreadyReturnedException(this.id);
        }

        this.returned = true;
        this.returnDate = returnDate;

        long daysLate = ChronoUnit.DAYS.between(
                this.borrowDate.plusDays(Penalty.LOAN_DURATION_DAYS),
                returnDate
        );

        boolean isLate = daysLate > 0;
        this.domainEvents.add(new BookReturned(memberId, isbn, returnDate, isLate));

        if (isLate) {
            this.penalty = Penalty.fromLateDays(daysLate);
            this.domainEvents.add(new PenaltyGenerated(memberId, penalty, daysLate));
        }

        return this.penalty;
    }

    public LocalDate getExpectedReturnDate() {
        return borrowDate.plusDays(Penalty.LOAN_DURATION_DAYS);
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return Collections.unmodifiableList(events);
    }

    public Long getId() { return id; }
    public MemberId getMemberId() { return memberId; }
    public ISBN getIsbn() { return isbn; }
    public LocalDate getBorrowDate() { return borrowDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public boolean isReturned() { return returned; }
    public Penalty getPenalty() { return penalty; }
}
