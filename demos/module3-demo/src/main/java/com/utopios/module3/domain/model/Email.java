package com.utopios.module3.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value Object immuable représentant une adresse email validée et normalisée.
 * Aucune dépendance Spring ou JPA — domaine pur.
 */
public record Email(String value) {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$"
    );

    public Email {
        Objects.requireNonNull(value, "L'email ne peut pas être null");
        String normalized = value.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Format d'email invalide : " + value);
        }
        value = normalized;
    }

    public static Email of(String value) {
        return new Email(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
