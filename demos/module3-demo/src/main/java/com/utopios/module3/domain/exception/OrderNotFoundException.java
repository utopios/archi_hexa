package com.utopios.module3.domain.exception;

/**
 * Exception levée quand une commande est introuvable.
 * Aucune dépendance Spring ou JPA — domaine pur.
 */
public class OrderNotFoundException extends RuntimeException {

    private final String orderId;

    public OrderNotFoundException(String orderId) {
        super("Commande introuvable : " + orderId);
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }
}
