package com.utopios.module3.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Value Object immuable représentant l'identifiant d'une commande.
 * Aucune dépendance Spring ou JPA — domaine pur.
 */
public record OrderId(String value) {

    public OrderId {
        Objects.requireNonNull(value, "OrderId cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException("OrderId cannot be blank");
    }

    public static OrderId of(String value) {
        return new OrderId(value);
    }

    public static OrderId generate() {
        return new OrderId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
