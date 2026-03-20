package com.bibliotech.domain.model;

import com.bibliotech.domain.exception.BorrowingLimitReachedException;
import com.bibliotech.domain.exception.UnpaidPenaltiesException;
import com.bibliotech.domain.vo.Email;
import com.bibliotech.domain.vo.MemberId;
import com.bibliotech.domain.vo.MemberName;
import com.bibliotech.domain.vo.Penalty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitaires du domaine — aucun framework, aucune BDD, aucun mock.
 */
@DisplayName("Member — entité du domaine")
class MemberTest {

    private static final MemberId MEMBER_ID = new MemberId("member-001");
    private static final MemberName NAME = new MemberName("Alice Dupont");
    private static final Email EMAIL = new Email("alice@bibliotech.fr");

    private Member freshMember;

    @BeforeEach
    void setUp() {
        // Membre sans emprunt, sans pénalité
        freshMember = Member.register(MEMBER_ID, NAME, EMAIL);
    }

    // -------------------------------------------------------------------------
    // canBorrow()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("canBorrow()")
    class CanBorrowTests {

        @Test
        @DisplayName("retourne true si le membre a moins de 3 livres ET pas de pénalités")
        void shouldReturnTrue_whenEligible() {
            assertThat(freshMember.canBorrow()).isTrue();
        }

        @Test
        @DisplayName("retourne false si le membre a 3 livres empruntés")
        void shouldReturnFalse_whenLimitReached() {
            Member member = memberWith3Books();

            assertThat(member.canBorrow()).isFalse();
        }

        @Test
        @DisplayName("retourne false si le membre a des pénalités impayées")
        void shouldReturnFalse_whenUnpaidPenalties() {
            Member member = memberWithPenalty(5.0);

            assertThat(member.canBorrow()).isFalse();
        }

        @Test
        @DisplayName("retourne false si à la fois 3 livres ET des pénalités")
        void shouldReturnFalse_whenBothConditions() {
            Member member = Member.reconstitute(MEMBER_ID, NAME, EMAIL, 3, new Penalty(5.0));

            assertThat(member.canBorrow()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // borrow()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("borrow()")
    class BorrowTests {

        @Test
        @DisplayName("incrémente le compteur d'emprunts")
        void shouldIncrementBorrowedCount() {
            int before = freshMember.getBorrowedBooksCount();

            freshMember.borrow();

            assertThat(freshMember.getBorrowedBooksCount()).isEqualTo(before + 1);
        }

        @Test
        @DisplayName("lance BorrowingLimitReachedException si la limite de 3 est atteinte")
        void shouldThrow_whenLimitReached() {
            Member member = memberWith3Books();

            assertThatThrownBy(member::borrow)
                    .isInstanceOf(BorrowingLimitReachedException.class);
        }

        @Test
        @DisplayName("lance UnpaidPenaltiesException si le membre a des pénalités impayées")
        void shouldThrow_whenUnpaidPenalties() {
            Member member = memberWithPenalty(3.0);

            assertThatThrownBy(member::borrow)
                    .isInstanceOf(UnpaidPenaltiesException.class);
        }

        @Test
        @DisplayName("ne modifie pas le compteur si BorrowingLimitReachedException est levée")
        void shouldNotChangeCount_whenLimitException() {
            Member member = memberWith3Books();
            int before = member.getBorrowedBooksCount();

            assertThatThrownBy(member::borrow).isInstanceOf(BorrowingLimitReachedException.class);

            assertThat(member.getBorrowedBooksCount()).isEqualTo(before);
        }

        @Test
        @DisplayName("ne modifie pas le compteur si UnpaidPenaltiesException est levée")
        void shouldNotChangeCount_whenPenaltyException() {
            Member member = memberWithPenalty(1.0);
            int before = member.getBorrowedBooksCount();

            assertThatThrownBy(member::borrow).isInstanceOf(UnpaidPenaltiesException.class);

            assertThat(member.getBorrowedBooksCount()).isEqualTo(before);
        }

        @Test
        @DisplayName("peut emprunter jusqu'à 3 livres successivement")
        void shouldAllowUpToThreeBooks() {
            assertThatNoException().isThrownBy(() -> {
                freshMember.borrow(); // 1
                freshMember.borrow(); // 2
                freshMember.borrow(); // 3
            });
            assertThat(freshMember.getBorrowedBooksCount()).isEqualTo(3);
        }
    }

    // -------------------------------------------------------------------------
    // returnBook()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("returnBook()")
    class ReturnBookTests {

        @Test
        @DisplayName("décrémente le compteur d'emprunts")
        void shouldDecrementBorrowedCount() {
            freshMember.borrow();
            int before = freshMember.getBorrowedBooksCount();

            freshMember.returnBook(freshMember.getUnpaidPenalties());

            assertThat(freshMember.getBorrowedBooksCount()).isEqualTo(before - 1);
        }

        @Test
        @DisplayName("rend le membre de nouveau empruntable après retour depuis la limite")
        void shouldAllowBorrowAgain_afterReturn() {
            Member member = memberWith3Books();
            assertThat(member.canBorrow()).isFalse();

            member.returnBook(member.getUnpaidPenalties());

            assertThat(member.canBorrow()).isTrue();
        }

        /*@Test
        @DisplayName("lance une exception si le membre n'a aucun emprunt en cours")
        void shouldThrow_whenNoBorrowings() {
            assertThatThrownBy(freshMember::returnBook)
                    .isInstanceOf(IllegalStateException.class);
        }*/
    }

    // -------------------------------------------------------------------------
    // addPenalty() / payPenalty()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("addPenalty() et payPenalty()")
    class PenaltyTests {

        @Test
        @DisplayName("addPenalty cumule les montants")
        void shouldAccumulatePenalties() {
            freshMember.addPenalty(new Penalty(3.0));
            freshMember.addPenalty(new Penalty(2.0));

            assertThat(freshMember.getUnpaidPenalties().amount()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("payPenalty réduit les pénalités du montant payé")
        void shouldReducePenalties_afterPayment() {
            freshMember.addPenalty(new Penalty(5.0));

            freshMember.payPenalty(3.0);

            assertThat(freshMember.getUnpaidPenalties().amount()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("payer toutes les pénalités rend le membre empruntable")
        void shouldAllowBorrow_afterFullPayment() {
            freshMember.addPenalty(new Penalty(5.0));
            assertThat(freshMember.canBorrow()).isFalse();

            freshMember.payPenalty(5.0);

            assertThat(freshMember.canBorrow()).isTrue();
        }

        @Test
        @DisplayName("payPenalty lance une exception si le montant dépasse les pénalités")
        void shouldThrow_whenPaymentExceedsPenalties() {
            freshMember.addPenalty(new Penalty(2.0));

            assertThatThrownBy(() -> freshMember.payPenalty(10.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Member memberWith3Books() {
        return Member.reconstitute(MEMBER_ID, NAME, EMAIL, 3, Penalty.zero());
    }

    private Member memberWithPenalty(double amount) {
        return Member.reconstitute(MEMBER_ID, NAME, EMAIL, 0, new Penalty(amount));
    }
}
