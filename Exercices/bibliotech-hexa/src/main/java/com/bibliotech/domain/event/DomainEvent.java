package com.bibliotech.domain.event;

import java.time.LocalDateTime;

public sealed interface DomainEvent permits BookBorrowed, BookReturned, PenaltyGenerated {
    LocalDateTime occurredAt();
}
