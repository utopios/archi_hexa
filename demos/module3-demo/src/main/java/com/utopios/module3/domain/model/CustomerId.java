package com.utopios.module3.domain.model;

import java.util.Objects;

/**
 * Value Object immuable représentant l'identifiant d'un client.
 * Aucune dépendance Spring ou JPA — domaine pur.
 */
public record CustomerId(String value) {

    public CustomerId {
        Objects.requireNonNull(value, "CustomerId cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException("CustomerId cannot be blank");
    }

    public static CustomerId of(String value) {
        return new CustomerId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
