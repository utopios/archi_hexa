package com.utopios.module3.domain.port.out;

/**
 * Port de sortie pour l'envoi de notifications.
 * Aucune dépendance Spring ou JPA — domaine pur.
 */
public interface NotificationPort {

    void sendConfirmation(String orderId, String customerEmail);
}
