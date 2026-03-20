package com.bibliotech.service;

import com.bibliotech.model.*;
import com.bibliotech.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BookService {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private BorrowingRecordRepository borrowingRecordRepository;

    @Autowired
    private JavaMailSender mailSender;

    // ---- CRUD Livres ----

    public Book addBook(Book book) {
        return bookRepository.save(book);
    }

    public Book getBook(String isbn) {
        return bookRepository.findById(isbn)
                .orElseThrow(() -> new RuntimeException("Livre non trouve: " + isbn));
    }

    public List<Book> searchBooks(String keyword) {
        return bookRepository.findByTitleContainingIgnoreCase(keyword);
    }

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    public void deleteBook(String isbn) {
        bookRepository.deleteById(isbn);
    }

    // ---- CRUD Membres ----

    public Member registerMember(Member member) {
        return memberRepository.save(member);
    }

    public Member getMember(String memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Membre non trouve: " + memberId));
    }

    public List<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    // ---- EMPRUNT ----

    @Transactional
    public BorrowingRecord borrowBook(String memberId, String isbn) {
        // Recuperer le membre
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Membre non trouve: " + memberId));

        // Recuperer le livre
        Book book = bookRepository.findById(isbn)
                .orElseThrow(() -> new RuntimeException("Livre non trouve: " + isbn));

        // Regle metier 1 : max 3 livres
        if (member.getBorrowedBooksCount() >= 3) {
            throw new RuntimeException("Le membre a deja emprunte 3 livres");
        }

        // Regle metier 2 : copies disponibles
        if (book.getAvailableCopies() <= 0) {
            throw new RuntimeException("Aucune copie disponible pour: " + book.getTitle());
        }

        // Regle metier 4 : penalites impayees
        if (member.getUnpaidPenalties() > 0) {
            throw new RuntimeException(
                "Le membre a des penalites impayees: " + member.getUnpaidPenalties() + " EUR"
            );
        }

        // Mise a jour des compteurs
        book.setAvailableCopies(book.getAvailableCopies() - 1);
        member.setBorrowedBooksCount(member.getBorrowedBooksCount() + 1);

        bookRepository.save(book);
        memberRepository.save(member);

        // Creer l'enregistrement d'emprunt
        BorrowingRecord record = new BorrowingRecord(member, book);
        borrowingRecordRepository.save(record);

        // Envoyer un email de confirmation
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(member.getEmail());
            message.setSubject("Confirmation d'emprunt - BiblioTech");
            message.setText("Bonjour " + member.getName() + ",\n\n"
                    + "Vous avez emprunte: " + book.getTitle() + "\n"
                    + "Date de retour prevue: " + LocalDate.now().plusDays(14) + "\n\n"
                    + "Bonne lecture !");
            mailSender.send(message);
        } catch (Exception e) {
            // On ignore l'erreur d'email... probleme !
            System.err.println("Erreur envoi email: " + e.getMessage());
        }

        return record;
    }

    // ---- RETOUR ----

    @Transactional
    public BorrowingRecord returnBook(Long borrowingId) {
        BorrowingRecord record = borrowingRecordRepository.findById(borrowingId)
                .orElseThrow(() -> new RuntimeException("Emprunt non trouve: " + borrowingId));

        if (record.isReturned()) {
            throw new RuntimeException("Ce livre a deja ete retourne");
        }

        // Marquer comme retourne
        record.setReturned(true);
        record.setReturnDate(LocalDate.now());

        // Regle metier 3 : calcul de penalite
        long daysLate = ChronoUnit.DAYS.between(
                record.getBorrowDate().plusDays(14),
                LocalDate.now()
        );
        if (daysLate > 0) {
            double penalty = daysLate * 1.0; // 1 EUR par jour de retard
            record.setPenalty(penalty);
            record.getMember().setUnpaidPenalties(
                    record.getMember().getUnpaidPenalties() + penalty
            );
            memberRepository.save(record.getMember());

            // Notification de penalite
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(record.getMember().getEmail());
                message.setSubject("Penalite de retard - BiblioTech");
                message.setText("Bonjour " + record.getMember().getName() + ",\n\n"
                        + "Votre retour du livre '" + record.getBook().getTitle()
                        + "' est en retard de " + daysLate + " jours.\n"
                        + "Penalite: " + penalty + " EUR\n\n"
                        + "Merci de regulariser votre situation.");
                mailSender.send(message);
            } catch (Exception e) {
                System.err.println("Erreur envoi email penalite: " + e.getMessage());
            }
        }

        // Remettre la copie en stock
        Book book = record.getBook();
        book.setAvailableCopies(book.getAvailableCopies() + 1);
        bookRepository.save(book);

        // Mettre a jour le compteur du membre
        Member member = record.getMember();
        member.setBorrowedBooksCount(member.getBorrowedBooksCount() - 1);
        memberRepository.save(member);

        borrowingRecordRepository.save(record);

        return record;
    }

    // ---- PAIEMENT PENALITE ----

    @Transactional
    public void payPenalty(String memberId, double amount) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Membre non trouve"));
        if (amount > member.getUnpaidPenalties()) {
            throw new RuntimeException("Montant superieur aux penalites dues");
        }
        member.setUnpaidPenalties(member.getUnpaidPenalties() - amount);
        memberRepository.save(member);
    }
}
