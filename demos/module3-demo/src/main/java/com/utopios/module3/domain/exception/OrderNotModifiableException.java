package com.utopios.module3.domain.exception;

/**
 * Exception levée quand on tente de modifier une commande dans un état non autorisé.
 * Aucune dépendance Spring ou JPA — domaine pur.
 */
public class OrderNotModifiableException extends RuntimeException {

    public OrderNotModifiableException(String message) {
        super(message);
    }
}
