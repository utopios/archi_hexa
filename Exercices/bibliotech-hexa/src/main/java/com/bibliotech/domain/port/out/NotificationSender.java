package com.bibliotech.domain.port.out;

import com.bibliotech.domain.event.DomainEvent;

public interface NotificationSender {
    void send(String to, String subject, String body);
    void notifyEvent(DomainEvent event);
}
