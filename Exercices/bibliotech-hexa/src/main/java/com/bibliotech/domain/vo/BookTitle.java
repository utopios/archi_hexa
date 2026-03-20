package com.bibliotech.domain.vo;

import java.util.Objects;

public record BookTitle(String value) {

    public BookTitle {
        Objects.requireNonNull(value, "Le titre ne peut pas etre null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Le titre ne peut pas etre vide");
        }
        if (value.length() > 500) {
            throw new IllegalArgumentException("Le titre ne peut pas depasser 500 caracteres");
        }
    }

    public static BookTitle of(String value) {
        return new BookTitle(value);
    }
}
