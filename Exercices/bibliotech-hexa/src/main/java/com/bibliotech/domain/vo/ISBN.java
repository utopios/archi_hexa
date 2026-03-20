package com.bibliotech.domain.vo;

import java.util.Objects;
import java.util.regex.Pattern;

public record ISBN(String value) {

    private static final Pattern ISBN_PATTERN =
        Pattern.compile("^(97[89])?\\d{9}(\\d|X)$");

    public ISBN {
        Objects.requireNonNull(value, "L'ISBN ne peut pas etre null");
        String cleaned = value.replaceAll("-", "");
        if (!ISBN_PATTERN.matcher(cleaned).matches()) {
            throw new IllegalArgumentException("ISBN invalide: " + value);
        }
        value = cleaned;
    }

    public static ISBN of(String value) {
        return new ISBN(value);
    }
}
