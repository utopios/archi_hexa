package com.bibliotech.domain.vo;

import java.util.Objects;
import java.util.UUID;

public record MemberId(String value) {

    public MemberId {
        Objects.requireNonNull(value, "Le MemberId ne peut pas etre null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Le MemberId ne peut pas etre vide");
        }
    }

    public static MemberId of(String value) {
        return new MemberId(value);
    }

    public static MemberId generate() {
        return new MemberId(UUID.randomUUID().toString());
    }
}
