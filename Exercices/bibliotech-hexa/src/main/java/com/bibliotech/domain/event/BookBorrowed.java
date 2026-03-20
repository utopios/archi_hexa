package com.bibliotech.domain.event;

import com.bibliotech.domain.vo.ISBN;
import com.bibliotech.domain.vo.MemberId;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookBorrowed(
        MemberId memberId,
        ISBN isbn,
        LocalDate borrowDate,
        LocalDate expectedReturnDate,
        LocalDateTime occurredAt
) implements DomainEvent {

    public BookBorrowed(MemberId memberId, ISBN isbn, LocalDate borrowDate) {
        this(memberId, isbn, borrowDate, borrowDate.plusDays(14), LocalDateTime.now());
    }
}
