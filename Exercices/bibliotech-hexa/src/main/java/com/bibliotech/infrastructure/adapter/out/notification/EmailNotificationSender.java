package com.bibliotech.infrastructure.adapter.out.notification;

import com.bibliotech.domain.event.BookBorrowed;
import com.bibliotech.domain.event.BookReturned;
import com.bibliotech.domain.event.DomainEvent;
import com.bibliotech.domain.event.PenaltyGenerated;
import com.bibliotech.domain.port.out.NotificationSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;

    public EmailNotificationSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Erreur envoi email: " + e.getMessage());
        }
    }

    @Override
    public void notifyEvent(DomainEvent event) {
        if (event instanceof BookBorrowed e) {
            send(
                    "notification@bibliotech.com",
                    "Confirmation d'emprunt - BiblioTech",
                    "Livre emprunte avec succes. Retour prevu le: " + e.expectedReturnDate()
            );
        } else if (event instanceof BookReturned e) {
            if (e.isLate()) {
                send("notification@bibliotech.com",
                        "Retour en retard - BiblioTech",
                        "Livre retourne en retard le " + e.returnDate());
            }
        } else if (event instanceof PenaltyGenerated e) {
            send(
                    "notification@bibliotech.com",
                    "Penalite de retard - BiblioTech",
                    "Penalite de " + e.penalty().amount() + " EUR (" + e.daysLate() + " jours de retard)"
            );
        }
    }
}
