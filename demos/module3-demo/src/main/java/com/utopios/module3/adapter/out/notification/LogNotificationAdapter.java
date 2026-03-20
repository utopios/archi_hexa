package com.utopios.module3.adapter.out.notification;

import com.utopios.module3.domain.port.out.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter de notification qui logue simplement la notification.
 * En production, ce serait remplacé par un envoi d'email réel ou un message broker.
 */
@Component
public class LogNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationAdapter.class);

    @Override
    public void sendConfirmation(String orderId, String customerEmail) {
        log.info("NOTIFICATION: Confirmation envoyée pour la commande {} à {}", orderId, customerEmail);
    }
}
