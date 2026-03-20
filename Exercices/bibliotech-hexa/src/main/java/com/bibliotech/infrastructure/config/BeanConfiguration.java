package com.bibliotech.infrastructure.config;

import com.bibliotech.application.usecase.BookCatalogUseCase;
import com.bibliotech.application.usecase.BookLendingUseCase;
import com.bibliotech.domain.port.in.BookCatalogService;
import com.bibliotech.domain.port.in.BookLendingService;
import com.bibliotech.domain.port.out.BookRepository;
import com.bibliotech.domain.port.out.BorrowingRepository;
import com.bibliotech.domain.port.out.MemberRepository;
import com.bibliotech.domain.port.out.NotificationSender;
import com.bibliotech.infrastructure.adapter.out.notification.EmailNotificationSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
public class BeanConfiguration {

    @Bean
    public BookCatalogService bookCatalogService(BookRepository bookRepository) {
        return new BookCatalogUseCase(bookRepository);
    }
    @Bean
    public BookLendingService bookLendingService(
            BookRepository bookRepository,
            MemberRepository memberRepository,
            BorrowingRepository borrowingRepository,
            NotificationSender notificationSender) {
        return new BookLendingUseCase(bookRepository, memberRepository, borrowingRepository, notificationSender);
    }

    @Bean
    public JavaMailSender mailSender() {
        JavaMailSender mailSender = new JavaMailSenderImpl();
        //Configuration SMTP
        return mailSender;
    }
}
