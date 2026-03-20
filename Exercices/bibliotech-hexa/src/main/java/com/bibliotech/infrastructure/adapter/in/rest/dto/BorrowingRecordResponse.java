package com.bibliotech.infrastructure.adapter.in.rest.dto;

import com.bibliotech.domain.model.BorrowingRecord;
import java.time.LocalDate;

public record BorrowingRecordResponse(
        Long id,
        String memberId,
        String isbn,
        LocalDate borrowDate,
        LocalDate returnDate,
        boolean returned,
        double penalty
) {
    public static BorrowingRecordResponse from(BorrowingRecord record) {
        return new BorrowingRecordResponse(
                record.getId(),
                record.getMemberId().value(),
                record.getIsbn().value(),
                record.getBorrowDate(),
                record.getReturnDate(),
                record.isReturned(),
                record.getPenalty().amount()
        );
    }
}
