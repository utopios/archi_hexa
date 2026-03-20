package com.utopios.module3.domain.service;

import com.utopios.module3.domain.model.CustomerId;
import com.utopios.module3.domain.model.Email;

import java.util.List;
import java.util.Objects;

/**
 * Objet de transfert pour la création d'une commande.
 * Aucune dépendance Spring ou JPA — domaine pur.
 */
public record CreateOrderCommand(CustomerId customerId, Email customerEmail, List<OrderItemCommand> items) {

    public CreateOrderCommand {
        Objects.requireNonNull(customerId, "L'identifiant client ne peut pas être null");
        Objects.requireNonNull(customerEmail, "L'email client ne peut pas être null");
        if (items == null) {
            throw new IllegalArgumentException("La liste des articles ne peut pas être null");
        }
        items = List.copyOf(items);
    }
}
