package com.bibliotech.domain.model;

import com.bibliotech.domain.exception.BookNotAvailableException;
import com.bibliotech.domain.vo.Author;
import com.bibliotech.domain.vo.BookTitle;
import com.bibliotech.domain.vo.ISBN;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitaires du domaine — aucun framework, aucune BDD, aucun mock.
 * Chaque test s'exécute en quelques millisecondes.
 */
@DisplayName("Book — entité du domaine")
class BookTest {

    private static final ISBN ISBN_CLEAN_CODE = new ISBN("9780132350884");
    private static final BookTitle TITLE = new BookTitle("Clean Code");
    private static final Author AUTHOR = new Author("Robert C. Martin");

    private Book bookWithCopies;
    private Book bookWithoutCopies;

    @BeforeEach
    void setUp() {
        bookWithCopies = Book.of(ISBN_CLEAN_CODE, TITLE, AUTHOR, 3);
        bookWithoutCopies = Book.of(ISBN_CLEAN_CODE, TITLE, AUTHOR, 0);
    }

    // -------------------------------------------------------------------------
    // canBeBorrowed()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("canBeBorrowed()")
    class CanBeBorrowedTests {

        @Test
        @DisplayName("retourne true si le nombre de copies disponibles est > 0")
        void shouldReturnTrue_whenCopiesAvailable() {
            assertThat(bookWithCopies.canBeBorrowed()).isTrue();
        }

        @Test
        @DisplayName("retourne false si le nombre de copies disponibles est 0")
        void shouldReturnFalse_whenNoCopiesAvailable() {
            assertThat(bookWithoutCopies.canBeBorrowed()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // decrementStock()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("decrementStock()")
    class DecrementStockTests {

        @Test
        @DisplayName("décrémente le stock de 1 lorsque des copies sont disponibles")
        void shouldDecrementStock_whenCopiesAvailable() {
            int before = bookWithCopies.getAvailableCopies();

            bookWithCopies.decrementStock();

            assertThat(bookWithCopies.getAvailableCopies()).isEqualTo(before - 1);
        }

        @Test
        @DisplayName("lance BookNotAvailableException si le stock est à 0")
        void shouldThrow_whenStockIsZero() {
            assertThatThrownBy(() -> bookWithoutCopies.decrementStock())
                    .isInstanceOf(BookNotAvailableException.class);
        }

        @Test
        @DisplayName("décrémente jusqu'à 0 sans exception")
        void shouldDecrementToZero_withoutException() {
            Book singleCopy = Book.of(ISBN_CLEAN_CODE, TITLE, AUTHOR, 1);

            singleCopy.decrementStock();

            assertThat(singleCopy.getAvailableCopies()).isZero();
            // Le suivant doit échouer
            assertThatThrownBy(singleCopy::decrementStock)
                    .isInstanceOf(BookNotAvailableException.class);
        }
    }

    // -------------------------------------------------------------------------
    // incrementStock()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("incrementStock()")
    class IncrementStockTests {

        @Test
        @DisplayName("incrémente le stock de 1")
        void shouldIncrementStock() {
            int before = bookWithCopies.getAvailableCopies();

            bookWithCopies.incrementStock();

            assertThat(bookWithCopies.getAvailableCopies()).isEqualTo(before + 1);
        }

        @Test
        @DisplayName("un livre sans copies devient empruntable après un retour")
        void shouldBecomeAvailable_afterReturn() {
            assertThat(bookWithoutCopies.canBeBorrowed()).isFalse();

            bookWithoutCopies.incrementStock();

            assertThat(bookWithoutCopies.canBeBorrowed()).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // Invariants de construction
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Invariants de construction")
    class ConstructionTests {

        @Test
        @DisplayName("lance une exception si le nombre de copies est négatif")
        void shouldThrow_whenNegativeCopies() {
            assertThatThrownBy(() -> Book.of(ISBN_CLEAN_CODE, TITLE, AUTHOR, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("accepte 0 copie à la création")
        void shouldAcceptZeroCopies() {
            assertThatNoException().isThrownBy(
                    () -> Book.of(ISBN_CLEAN_CODE, TITLE, AUTHOR, 0));
        }
    }
}
