package com.utopios.module3.domain.model;

import java.util.Objects;

/**
 * Value Object représentant un article dans une commande.
 * Aucune dépendance Spring ou JPA — domaine pur.
 */
public record OrderItem(ProductId productId, String name, Money unitPrice, int quantity) {

    public OrderItem {
        Objects.requireNonNull(productId, "L'identifiant produit ne peut pas être null");
        Objects.requireNonNull(name, "Le nom du produit ne peut pas être null");
        Objects.requireNonNull(unitPrice, "Le prix unitaire ne peut pas être null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Le nom du produit ne peut pas être vide");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("La quantité doit être supérieure à 0, reçu : " + quantity);
        }
    }

    public static OrderItem of(ProductId productId, String name, Money unitPrice, int quantity) {
        return new OrderItem(productId, name, unitPrice, quantity);
    }

    /**
     * Calcule le sous-total de la ligne (prix unitaire × quantité).
     */
    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }
}
