package com.utopios.module3.domain.model;

import java.util.Objects;

/**
 * Value Object immuable représentant l'identifiant d'un produit.
 * Aucune dépendance Spring ou JPA — domaine pur.
 */
public record ProductId(String value) {

    public ProductId {
        Objects.requireNonNull(value, "ProductId cannot be null");
        if (value.isBlank()) throw new IllegalArgumentException("ProductId cannot be blank");
    }

    public static ProductId of(String value) {
        return new ProductId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
