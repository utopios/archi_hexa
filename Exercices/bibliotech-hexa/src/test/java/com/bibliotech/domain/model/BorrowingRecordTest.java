package com.bibliotech.domain.model;

import com.bibliotech.domain.event.BookBorrowed;
import com.bibliotech.domain.event.BookReturned;
import com.bibliotech.domain.event.DomainEvent;
import com.bibliotech.domain.event.PenaltyGenerated;
import com.bibliotech.domain.exception.BookAlreadyReturnedException;
import com.bibliotech.domain.vo.ISBN;
import com.bibliotech.domain.vo.MemberId;
import com.bibliotech.domain.vo.Penalty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitaires du domaine — focus sur la production d'événements.
 * Aucun framework, aucune BDD.
 */
@DisplayName("BorrowingRecord — entité avec événements du domaine")
class BorrowingRecordTest {

    private static final MemberId MEMBER_ID = new MemberId("member-001");
    private static final ISBN ISBN_VALUE = new ISBN("9780132350884");
    private static final LocalDate BORROW_DATE = LocalDate.of(2024, 1, 1);

    // Retour dans les délais (jour 14 = dernier jour acceptable)
    private static final LocalDate ON_TIME_RETURN = BORROW_DATE.plusDays(14);

    // Retour en retard de 5 jours
    private static final LocalDate LATE_RETURN = BORROW_DATE.plusDays(19);

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("génère un événement BookBorrowed")
        void shouldGenerateBookBorrowedEvent() {
            BorrowingRecord record = BorrowingRecord.create(MEMBER_ID, ISBN_VALUE, BORROW_DATE);

            List<DomainEvent> events = record.pullDomainEvents();

            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(BookBorrowed.class);
        }

        @Test
        @DisplayName("l'événement BookBorrowed contient les bonnes données")
        void shouldGenerateBookBorrowedWithCorrectData() {
            BorrowingRecord record = BorrowingRecord.create(MEMBER_ID, ISBN_VALUE, BORROW_DATE);

            BookBorrowed event = (BookBorrowed) record.pullDomainEvents().get(0);

            assertThat(event.memberId()).isEqualTo(MEMBER_ID);
            assertThat(event.isbn()).isEqualTo(ISBN_VALUE);
            assertThat(event.borrowDate()).isEqualTo(BORROW_DATE);
        }

        @Test
        @DisplayName("la liste d'événements est vide après pullDomainEvents()")
        void shouldClearEventsAfterPull() {
            BorrowingRecord record = BorrowingRecord.create(MEMBER_ID, ISBN_VALUE, BORROW_DATE);

            record.pullDomainEvents(); // premier pull
            List<DomainEvent> secondPull = record.pullDomainEvents();

            assertThat(secondPull).isEmpty();
        }

        @Test
        @DisplayName("le record n'est pas marqué comme retourné à la création")
        void shouldNotBeReturned_atCreation() {
            BorrowingRecord record = BorrowingRecord.create(MEMBER_ID, ISBN_VALUE, BORROW_DATE);

            assertThat(record.isReturned()).isFalse();
        }

        @Test
        @DisplayName("la pénalité initiale est zéro")
        void shouldHaveZeroPenalty_atCreation() {
            BorrowingRecord record = BorrowingRecord.create(MEMBER_ID, ISBN_VALUE, BORROW_DATE);

            assertThat(record.getPenalty().amount()).isZero();
        }
    }

    @Nested
    @DisplayName("markAsReturned() — retour dans les délais")
    class OnTimeReturnTests {

        private BorrowingRecord record;

        @BeforeEach
        void setUp() {
            record = BorrowingRecord.create(MEMBER_ID, ISBN_VALUE, BORROW_DATE);
            record.pullDomainEvents(); // vider les événements de création
        }

        @Test
        @DisplayName("génère uniquement un événement BookReturned (pas de PenaltyGenerated)")
        void shouldGenerateOnlyBookReturnedEvent() {
            record.markAsReturned(ON_TIME_RETURN);

            List<DomainEvent> events = record.pullDomainEvents();

            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(BookReturned.class);
        }

        @Test
        @DisplayName("l'événement BookReturned indique isLate = false")
        void shouldIndicateNotLate_inEvent() {
            record.markAsReturned(ON_TIME_RETURN);

            BookReturned event = (BookReturned) record.pullDomainEvents().get(0);

            assertThat(event.isLate()).isFalse();
        }

        @Test
        @DisplayName("ne génère pas de pénalité (montant = 0)")
        void shouldHaveZeroPenalty() {
            Penalty penalty = record.markAsReturned(ON_TIME_RETURN);

            assertThat(penalty.amount()).isZero();
            assertThat(penalty.isUnpaid()).isFalse();
        }

        @Test
        @DisplayName("marque le record comme retourné")
        void shouldMarkAsReturned() {
            record.markAsReturned(ON_TIME_RETURN);

            assertThat(record.isReturned()).isTrue();
            assertThat(record.getReturnDate()).isEqualTo(ON_TIME_RETURN);
        }
    }

    @Nested
    @DisplayName("markAsReturned() — retour en retard")
    class LateReturnTests {

        private BorrowingRecord record;

        @BeforeEach
        void setUp() {
            record = BorrowingRecord.create(MEMBER_ID, ISBN_VALUE, BORROW_DATE);
            record.pullDomainEvents(); // vider les événements de création
        }

        @Test
        @DisplayName("génère BookReturned + PenaltyGenerated (2 événements)")
        void shouldGenerateTwoEvents() {
            record.markAsReturned(LATE_RETURN);

            List<DomainEvent> events = record.pullDomainEvents();

            assertThat(events).hasSize(2);
            assertThat(events.get(0)).isInstanceOf(BookReturned.class);
            assertThat(events.get(1)).isInstanceOf(PenaltyGenerated.class);
        }

        @Test
        @DisplayName("l'événement BookReturned indique isLate = true")
        void shouldIndicateLate_inEvent() {
            record.markAsReturned(LATE_RETURN);

            BookReturned event = (BookReturned) record.pullDomainEvents().get(0);

            assertThat(event.isLate()).isTrue();
        }

        @Test
        @DisplayName("calcule la pénalité à 1€ par jour de retard (5 jours = 5€)")
        void shouldCalculateCorrectPenalty() {
            // LATE_RETURN = BORROW_DATE + 19 jours, durée = 14 jours → 5 jours de retard
            Penalty penalty = record.markAsReturned(LATE_RETURN);

            assertThat(penalty.amount()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("l'événement PenaltyGenerated contient le bon montant et les bons jours")
        void shouldGeneratePenaltyEventWithCorrectData() {
            record.markAsReturned(LATE_RETURN);

            List<DomainEvent> events = record.pullDomainEvents();
            PenaltyGenerated penaltyEvent = (PenaltyGenerated) events.get(1);

            assertThat(penaltyEvent.memberId()).isEqualTo(MEMBER_ID);
            assertThat(penaltyEvent.daysLate()).isEqualTo(5L);
            assertThat(penaltyEvent.penalty().amount()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("1 jour de retard = 1€ de pénalité")
        void shouldCalculateOneDayLatePenalty() {
            LocalDate oneDayLate = BORROW_DATE.plusDays(15); // 14 + 1

            Penalty penalty = record.markAsReturned(oneDayLate);

            assertThat(penalty.amount()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("markAsReturned() — déjà retourné")
    class AlreadyReturnedTests {

        @Test
        @DisplayName("lance BookAlreadyReturnedException si déjà retourné")
        void shouldThrow_whenAlreadyReturned() {
            BorrowingRecord record = BorrowingRecord.create(MEMBER_ID, ISBN_VALUE, BORROW_DATE);
            record.pullDomainEvents();
            record.markAsReturned(ON_TIME_RETURN);
            record.pullDomainEvents();

            assertThatThrownBy(() -> record.markAsReturned(ON_TIME_RETURN))
                    .isInstanceOf(BookAlreadyReturnedException.class);
        }
    }
}
