package com.utopios.module3.domain.service;

import com.utopios.module3.domain.model.ProductId;

import java.util.Objects;

/**
 * Objet de transfert représentant un article dans une commande de création.
 * Aucune dépendance Spring ou JPA — domaine pur.
 */
public record OrderItemCommand(ProductId productId, String name, double unitPrice, int quantity) {

    public OrderItemCommand {
        Objects.requireNonNull(productId, "L'identifiant produit ne peut pas être null");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom du produit ne peut pas être null ou vide");
        }
        if (unitPrice < 0) {
            throw new IllegalArgumentException("Le prix unitaire ne peut pas être négatif : " + unitPrice);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("La quantité doit être supérieure à 0 : " + quantity);
        }
    }
}
