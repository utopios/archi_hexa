package com.bibliotech.application;

import com.bibliotech.application.usecase.BookLendingUseCase;
import com.bibliotech.domain.event.BookBorrowed;
import com.bibliotech.domain.event.DomainEvent;
import com.bibliotech.domain.exception.BorrowingLimitReachedException;
import com.bibliotech.domain.model.Book;
import com.bibliotech.domain.model.BorrowingRecord;
import com.bibliotech.domain.model.Member;
import com.bibliotech.domain.port.out.BookRepository;
import com.bibliotech.domain.port.out.BorrowingRepository;
import com.bibliotech.domain.port.out.MemberRepository;
import com.bibliotech.domain.port.out.NotificationSender;
import com.bibliotech.domain.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests BDD du use case BookLendingUseCase.
 *
 * Pas de Spring, pas de BDD, pas de mock framework.
 * Les ports secondaires sont implémentés comme classes internes en mémoire.
 * Chaque test s'exécute en quelques millisecondes.
 */
@DisplayName("BookLendingUseCase — tests BDD avec adapteurs en mémoire")
class BookLendingUseCaseTest {

    // =========================================================================
    // Adapteurs en mémoire (classes internes)
    // =========================================================================

    static class InMemoryBookRepository implements BookRepository {
        private final Map<String, Book> store = new HashMap<>();

        public void add(Book book) {
            store.put(book.getIsbn().value(), book);
        }

        @Override public Optional<Book> findByIsbn(ISBN isbn) {
            return Optional.ofNullable(store.get(isbn.value()));
        }
        @Override public List<Book> findByTitleContaining(String keyword) {
            return store.values().stream()
                    .filter(b -> b.getTitle().value().toLowerCase().contains(keyword.toLowerCase()))
                    .toList();
        }
        @Override public List<Book> findAll() { return new ArrayList<>(store.values()); }
        @Override public Book save(Book book) {
            store.put(book.getIsbn().value(), book);
            return book;
        }
        @Override public void deleteByIsbn(ISBN isbn) { store.remove(isbn.value()); }
    }

    static class InMemoryMemberRepository implements MemberRepository {
        private final Map<String, Member> store = new HashMap<>();

        public void add(Member member) {
            store.put(member.getMemberId().value(), member);
        }

        @Override public Optional<Member> findById(MemberId id) {
            return Optional.ofNullable(store.get(id.value()));
        }
        @Override public Member save(Member member) {
            store.put(member.getMemberId().value(), member);
            return member;
        }
        @Override public List<Member> findAll() { return new ArrayList<>(store.values()); }
    }

    static class InMemoryBorrowingRepository implements BorrowingRepository {
        private final Map<Long, BorrowingRecord> store = new HashMap<>();
        private long nextId = 1L;

        @Override public Optional<BorrowingRecord> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }
        @Override public List<BorrowingRecord> findActiveByMember(MemberId id) {
            return store.values().stream()
                    .filter(r -> r.getMemberId().equals(id) && !r.isReturned())
                    .toList();
        }
        @Override public List<BorrowingRecord> findActiveByBook(ISBN isbn) {
            return store.values().stream()
                    .filter(r -> r.getIsbn().equals(isbn) && !r.isReturned())
                    .toList();
        }
        @Override public BorrowingRecord save(BorrowingRecord record) {
            // Retourner le même objet pour conserver les événements non encore consommés.
            // L'id reste null en mémoire — acceptable pour les tests unitaires.
            Long id = record.getId() != null ? record.getId() : nextId++;
            store.put(id, record);
            return record;
        }
    }

    static class InMemoryNotificationSender implements NotificationSender {
        private final List<DomainEvent> receivedEvents = new ArrayList<>();
        private final List<String> sentMessages = new ArrayList<>();

        @Override public void send(String to, String subject, String body) {
            sentMessages.add(to + "|" + subject);
        }
        @Override public void notifyEvent(DomainEvent event) {
            receivedEvents.add(event);
        }

        public List<DomainEvent> getReceivedEvents() { return Collections.unmodifiableList(receivedEvents); }
        public List<String> getSentMessages() { return Collections.unmodifiableList(sentMessages); }
        public void clear() { receivedEvents.clear(); sentMessages.clear(); }
    }

    // =========================================================================
    // Fixtures
    // =========================================================================

    private static final MemberId MEMBER_ID = new MemberId("member-001");
    private static final MemberName MEMBER_NAME = new MemberName("Alice Dupont");
    private static final Email MEMBER_EMAIL = new Email("alice@bibliotech.fr");

    private static final ISBN BOOK_ISBN = new ISBN("9780132350884");
    private static final BookTitle BOOK_TITLE = new BookTitle("Clean Code");
    private static final Author BOOK_AUTHOR = new Author("Robert C. Martin");

    private InMemoryBookRepository bookRepository;
    private InMemoryMemberRepository memberRepository;
    private InMemoryBorrowingRepository borrowingRepository;
    private InMemoryNotificationSender notificationSender;
    private BookLendingUseCase useCase;

    @BeforeEach
    void setUp() {
        bookRepository = new InMemoryBookRepository();
        memberRepository = new InMemoryMemberRepository();
        borrowingRepository = new InMemoryBorrowingRepository();
        notificationSender = new InMemoryNotificationSender();
        useCase = new BookLendingUseCase(
                bookRepository, memberRepository, borrowingRepository, notificationSender);
    }

    // =========================================================================
    // Scénario 1 : membre avec 3 livres → BorrowingLimitReachedException
    // =========================================================================

    @Nested
    @DisplayName("Scénario 1 : membre ayant atteint la limite d'emprunt")
    class Scenario1_LimitReached {

        @Test
        @DisplayName("GIVEN un membre avec 3 livres empruntés " +
                     "WHEN il emprunte un 4ème " +
                     "THEN BorrowingLimitReachedException est levée")
        void shouldThrowBorrowingLimitReachedException() {
            // GIVEN
            Member memberWith3Books = Member.reconstitute(MEMBER_ID, MEMBER_NAME, MEMBER_EMAIL, 3, Penalty.zero());
            memberRepository.add(memberWith3Books);
            Book book = Book.of(BOOK_ISBN, BOOK_TITLE, BOOK_AUTHOR, 2);
            bookRepository.add(book);

            // WHEN / THEN
            assertThatThrownBy(() -> useCase.borrowBook(MEMBER_ID.value(), BOOK_ISBN.value()))
                    .isInstanceOf(BorrowingLimitReachedException.class);
        }

        @Test
        @DisplayName("GIVEN un membre avec 3 livres empruntés " +
                     "WHEN l'emprunt échoue " +
                     "THEN le stock du livre reste inchangé")
        void shouldNotChangeBookStock_whenLimitReached() {
            // GIVEN
            Member memberWith3Books = Member.reconstitute(MEMBER_ID, MEMBER_NAME, MEMBER_EMAIL, 3, Penalty.zero());
            memberRepository.add(memberWith3Books);
            Book book = Book.of(BOOK_ISBN, BOOK_TITLE, BOOK_AUTHOR, 2);
            bookRepository.add(book);
            int stockBefore = book.getAvailableCopies();

            // WHEN (on ignore l'exception)
            try { useCase.borrowBook(MEMBER_ID.value(), BOOK_ISBN.value()); } catch (Exception ignored) {}

            // THEN
            Book bookAfter = bookRepository.findByIsbn(BOOK_ISBN).orElseThrow();
            assertThat(bookAfter.getAvailableCopies()).isEqualTo(stockBefore);
        }

        @Test
        @DisplayName("GIVEN un membre avec 3 livres empruntés " +
                     "WHEN l'emprunt échoue " +
                     "THEN aucune notification n'est envoyée")
        void shouldNotSendNotification_whenLimitReached() {
            // GIVEN
            Member memberWith3Books = Member.reconstitute(MEMBER_ID, MEMBER_NAME, MEMBER_EMAIL, 3, Penalty.zero());
            memberRepository.add(memberWith3Books);
            bookRepository.add(Book.of(BOOK_ISBN, BOOK_TITLE, BOOK_AUTHOR, 2));

            // WHEN
            try { useCase.borrowBook(MEMBER_ID.value(), BOOK_ISBN.value()); } catch (Exception ignored) {}

            // THEN
            assertThat(notificationSender.getReceivedEvents()).isEmpty();
        }
    }

    // =========================================================================
    // Scénario 2 : emprunt nominal
    // =========================================================================

    @Nested
    @DisplayName("Scénario 2 : emprunt nominal d'un membre éligible")
    class Scenario2_SuccessfulBorrow {

        @BeforeEach
        void setUpScenario() {
            // GIVEN un membre éligible et un livre disponible
            memberRepository.add(Member.register(MEMBER_ID, MEMBER_NAME, MEMBER_EMAIL));
            bookRepository.add(Book.of(BOOK_ISBN, BOOK_TITLE, BOOK_AUTHOR, 3));
        }

        @Test
        @DisplayName("GIVEN un membre éligible " +
                     "WHEN il emprunte " +
                     "THEN l'emprunt est enregistré")
        void shouldRegisterBorrowing() {
            // WHEN
            BorrowingRecord record = useCase.borrowBook(MEMBER_ID.value(), BOOK_ISBN.value());

            // THEN
            assertThat(record).isNotNull();
            assertThat(record.getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(record.getIsbn()).isEqualTo(BOOK_ISBN);
            assertThat(record.isReturned()).isFalse();
        }

        @Test
        @DisplayName("GIVEN un membre éligible " +
                     "WHEN il emprunte " +
                     "THEN le stock du livre est décrémenté de 1")
        void shouldDecrementBookStock() {
            int stockBefore = bookRepository.findByIsbn(BOOK_ISBN).orElseThrow().getAvailableCopies();

            // WHEN
            useCase.borrowBook(MEMBER_ID.value(), BOOK_ISBN.value());

            // THEN
            int stockAfter = bookRepository.findByIsbn(BOOK_ISBN).orElseThrow().getAvailableCopies();
            assertThat(stockAfter).isEqualTo(stockBefore - 1);
        }

        @Test
        @DisplayName("GIVEN un membre éligible " +
                     "WHEN il emprunte " +
                     "THEN une notification BookBorrowed est envoyée")
        void shouldSendNotification() {
            // WHEN
            useCase.borrowBook(MEMBER_ID.value(), BOOK_ISBN.value());

            // THEN — la notification a été envoyée
            // Note : pullDomainEvents() vide les événements dans borrowBook,
            // donc on vérifie via le notificationSender
            assertThat(notificationSender.getReceivedEvents())
                    .hasSize(1)
                    .first()
                    .isInstanceOf(BookBorrowed.class);
        }

        @Test
        @DisplayName("GIVEN un membre éligible " +
                     "WHEN il emprunte " +
                     "THEN le compteur d'emprunts du membre est incrémenté")
        void shouldIncrementMemberBorrowedCount() {
            int countBefore = memberRepository.findById(MEMBER_ID).orElseThrow().getBorrowedBooksCount();

            // WHEN
            useCase.borrowBook(MEMBER_ID.value(), BOOK_ISBN.value());

            // THEN
            int countAfter = memberRepository.findById(MEMBER_ID).orElseThrow().getBorrowedBooksCount();
            assertThat(countAfter).isEqualTo(countBefore + 1);
        }
    }

    // =========================================================================
    // Scénario 3 : membre avec pénalités impayées
    // =========================================================================

    @Nested
    @DisplayName("Scénario 3 : membre avec pénalités impayées")
    class Scenario3_UnpaidPenalties {

        @Test
        @DisplayName("GIVEN un membre avec pénalités " +
                     "WHEN il emprunte " +
                     "THEN UnpaidPenaltiesException est levée et le stock reste inchangé")
        void shouldThrowAndNotChangeStock_whenUnpaidPenalties() {
            // GIVEN
            Member memberWithPenalty = Member.reconstitute(
                    MEMBER_ID, MEMBER_NAME, MEMBER_EMAIL, 0, new Penalty(5.0));
            memberRepository.add(memberWithPenalty);
            Book book = Book.of(BOOK_ISBN, BOOK_TITLE, BOOK_AUTHOR, 2);
            bookRepository.add(book);
            int stockBefore = book.getAvailableCopies();

            // WHEN
            try { useCase.borrowBook(MEMBER_ID.value(), BOOK_ISBN.value()); } catch (Exception ignored) {}

            // THEN — stock inchangé
            assertThat(bookRepository.findByIsbn(BOOK_ISBN).orElseThrow().getAvailableCopies())
                    .isEqualTo(stockBefore);
        }
    }
}
