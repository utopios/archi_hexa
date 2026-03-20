package com.bibliotech.domain.event;

import com.bibliotech.domain.vo.ISBN;
import com.bibliotech.domain.vo.MemberId;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookReturned(
        MemberId memberId,
        ISBN isbn,
        LocalDate returnDate,
        boolean isLate,
        LocalDateTime occurredAt
) implements DomainEvent {

    public BookReturned(MemberId memberId, ISBN isbn, LocalDate returnDate, boolean isLate) {
        this(memberId, isbn, returnDate, isLate, LocalDateTime.now());
    }
}
