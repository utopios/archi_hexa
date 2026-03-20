package com.bibliotech.infrastructure.adapter.out.persistence;

import com.bibliotech.domain.model.Book;
import com.bibliotech.domain.vo.Author;
import com.bibliotech.domain.vo.BookTitle;
import com.bibliotech.domain.vo.ISBN;
import com.bibliotech.infrastructure.adapter.out.persistence.entity.BookJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Test d'intégration JPA avec H2 en mémoire.
 *
 * @DataJpaTest démarre uniquement la couche JPA (Hibernate + H2).
 * Pas de contrôleur, pas de service, pas de contexte Spring complet.
 * Plus rapide qu'un @SpringBootTest (~1s vs ~5s).
 */
@DataJpaTest
@Import(JpaBookRepository.class)
@DisplayName("JpaBookRepository — test d'intégration avec H2")
class JpaBookRepositoryIntegrationTest {

    @Autowired
    private JpaBookRepository jpaBookRepository;

    @Autowired
    private SpringDataBookRepository springDataRepository;

    private static final ISBN CLEAN_CODE_ISBN = new ISBN("9780132350884");
    private static final ISBN DDD_ISBN = new ISBN("9780321125217");
    private static final ISBN PRAGMATIC_ISBN = new ISBN("9780201633610");

    @BeforeEach
    void setUp() {
        springDataRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // save() + findByIsbn()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("save() et findByIsbn()")
    class SaveAndFindTests {

        @Test
        @DisplayName("save() persiste le livre et findByIsbn() le retrouve")
        void shouldPersistAndFindBook() {
            // GIVEN
            Book book = Book.of(CLEAN_CODE_ISBN,
                    new BookTitle("Clean Code"),
                    new Author("Robert C. Martin"), 3);

            // WHEN
            jpaBookRepository.save(book);
            Optional<Book> found = jpaBookRepository.findByIsbn(CLEAN_CODE_ISBN);

            // THEN
            assertThat(found).isPresent();
            assertThat(found.get().getIsbn()).isEqualTo(CLEAN_CODE_ISBN);
            assertThat(found.get().getTitle().value()).isEqualTo("Clean Code");
            assertThat(found.get().getAuthor().value()).isEqualTo("Robert C. Martin");
            assertThat(found.get().getAvailableCopies()).isEqualTo(3);
        }

        @Test
        @DisplayName("findByIsbn() retourne Optional.empty() si le livre n'existe pas")
        void shouldReturnEmpty_whenNotFound() {
            Optional<Book> found = jpaBookRepository.findByIsbn(CLEAN_CODE_ISBN);

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("save() met à jour un livre existant (upsert)")
        void shouldUpdateExistingBook() {
            Book original = Book.of(CLEAN_CODE_ISBN, new BookTitle("Clean Code"),
                    new Author("Robert C. Martin"), 3);
            jpaBookRepository.save(original);

            // Modifier le stock
            original.decrementStock();
            jpaBookRepository.save(original);

            Optional<Book> found = jpaBookRepository.findByIsbn(CLEAN_CODE_ISBN);
            assertThat(found).isPresent();
            assertThat(found.get().getAvailableCopies()).isEqualTo(2);
        }
    }

    // -------------------------------------------------------------------------
    // findByTitleContaining()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findByTitleContaining()")
    class FindByTitleTests {

        @BeforeEach
        void populateBooks() {
            jpaBookRepository.save(Book.of(CLEAN_CODE_ISBN,
                    new BookTitle("Clean Code"), new Author("Robert C. Martin"), 2));
            jpaBookRepository.save(Book.of(DDD_ISBN,
                    new BookTitle("Domain-Driven Design"), new Author("Eric Evans"), 1));
            jpaBookRepository.save(Book.of(PRAGMATIC_ISBN,
                    new BookTitle("The Pragmatic Programmer"), new Author("David Thomas"), 3));
        }

        @Test
        @DisplayName("retourne les livres dont le titre contient le mot-clé (sensible à la casse ignorée)")
        void shouldFindByTitleKeyword() {
            List<Book> results = jpaBookRepository.findByTitleContaining("clean");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getTitle().value()).isEqualTo("Clean Code");
        }

        @Test
        @DisplayName("la recherche est insensible à la casse")
        void shouldBeCaseInsensitive() {
            List<Book> lowerCase = jpaBookRepository.findByTitleContaining("domain");
            List<Book> upperCase = jpaBookRepository.findByTitleContaining("DOMAIN");
            List<Book> mixedCase = jpaBookRepository.findByTitleContaining("Domain");

            assertThat(lowerCase).hasSize(1);
            assertThat(upperCase).hasSize(1);
            assertThat(mixedCase).hasSize(1);
        }

        @Test
        @DisplayName("retourne plusieurs résultats si le mot-clé correspond à plusieurs livres")
        void shouldReturnMultipleResults_whenKeywordMatchesMultiple() {
            // "e" est dans "Clean Code" et "The Pragmatic Programmer" et "Domain-Driven Design"
            List<Book> results = jpaBookRepository.findByTitleContaining("e");

            assertThat(results).hasSizeGreaterThan(1);
        }

        @Test
        @DisplayName("retourne une liste vide si aucun titre ne correspond")
        void shouldReturnEmpty_whenNoMatch() {
            List<Book> results = jpaBookRepository.findByTitleContaining("Kubernetes");

            assertThat(results).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // findAll()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findAll()")
    class FindAllTests {

        @Test
        @DisplayName("retourne tous les livres persistés")
        void shouldReturnAllBooks() {
            jpaBookRepository.save(Book.of(CLEAN_CODE_ISBN, new BookTitle("Clean Code"),
                    new Author("Robert C. Martin"), 2));
            jpaBookRepository.save(Book.of(DDD_ISBN, new BookTitle("Domain-Driven Design"),
                    new Author("Eric Evans"), 1));

            List<Book> all = jpaBookRepository.findAll();

            assertThat(all).hasSize(2);
        }

        @Test
        @DisplayName("retourne une liste vide si aucun livre n'est persisté")
        void shouldReturnEmpty_whenNoBooks() {
            List<Book> all = jpaBookRepository.findAll();

            assertThat(all).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // deleteByIsbn()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("deleteByIsbn()")
    class DeleteTests {

        @Test
        @DisplayName("supprime le livre et le rend introuvable")
        void shouldDeleteBook() {
            jpaBookRepository.save(Book.of(CLEAN_CODE_ISBN, new BookTitle("Clean Code"),
                    new Author("Robert C. Martin"), 2));

            jpaBookRepository.deleteByIsbn(CLEAN_CODE_ISBN);

            assertThat(jpaBookRepository.findByIsbn(CLEAN_CODE_ISBN)).isEmpty();
        }
    }
}
