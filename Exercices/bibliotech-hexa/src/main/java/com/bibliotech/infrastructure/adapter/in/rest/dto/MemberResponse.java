package com.bibliotech.infrastructure.adapter.in.rest.dto;

import com.bibliotech.domain.model.Member;

public record MemberResponse(
        String memberId,
        String name,
        String email,
        int borrowedBooksCount,
        double unpaidPenalties
) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(
                member.getMemberId().value(),
                member.getName().value(),
                member.getEmail().value(),
                member.getBorrowedBooksCount(),
                member.getUnpaidPenalties().amount()
        );
    }
}
