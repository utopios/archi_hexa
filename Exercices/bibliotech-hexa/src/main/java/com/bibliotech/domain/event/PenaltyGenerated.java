package com.bibliotech.domain.event;

import com.bibliotech.domain.vo.MemberId;
import com.bibliotech.domain.vo.Penalty;
import java.time.LocalDateTime;

public record PenaltyGenerated(
        MemberId memberId,
        Penalty penalty,
        long daysLate,
        LocalDateTime occurredAt
) implements DomainEvent {

    public PenaltyGenerated(MemberId memberId, Penalty penalty, long daysLate) {
        this(memberId, penalty, daysLate, LocalDateTime.now());
    }
}
