package com.bibliotech;

import com.bibliotech.model.Book;
import com.bibliotech.model.Member;
import com.bibliotech.model.BorrowingRecord;
import com.bibliotech.service.BookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class BiblioTechApplicationTest {

    @Autowired
    private BookService bookService;

    @MockBean
    private JavaMailSender mailSender;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldAddAndRetrieveBook() {
        Book book = new Book("9782070368228", "Le Petit Prince", "Antoine de Saint-Exupery", 3);
        bookService.addBook(book);

        Book found = bookService.getBook("9782070368228");
        assertThat(found.getTitle()).isEqualTo("Le Petit Prince");
        assertThat(found.getAvailableCopies()).isEqualTo(3);
    }

    @Test
    void shouldBorrowBook() {
        Book book = new Book("9780061965784", "Brave New World", "Aldous Huxley", 2);
        bookService.addBook(book);

        Member member = new Member("Alice Dupont", "alice@example.com");
        Member savedMember = bookService.registerMember(member);

        BorrowingRecord record = bookService.borrowBook(savedMember.getMemberId(), "9780061965784");

        assertThat(record).isNotNull();
        assertThat(record.getBook().getTitle()).isEqualTo("Brave New World");

        Book updatedBook = bookService.getBook("9780061965784");
        assertThat(updatedBook.getAvailableCopies()).isEqualTo(1);
    }

    @Test
    void shouldRejectBorrowWhenNoCopiesAvailable() {
        Book book = new Book("9780743273565", "The Great Gatsby", "F. Scott Fitzgerald", 0);
        bookService.addBook(book);

        Member member = new Member("Bob Martin", "bob@example.com");
        Member savedMember = bookService.registerMember(member);

        assertThatThrownBy(() -> bookService.borrowBook(savedMember.getMemberId(), "9780743273565"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Aucune copie disponible");
    }

    @Test
    void shouldRejectBorrowWhenLimitReached() {
        bookService.addBook(new Book("9780451524935", "1984", "George Orwell", 1));
        bookService.addBook(new Book("9780316769174", "The Catcher in the Rye", "J.D. Salinger", 1));
        bookService.addBook(new Book("9780060935467", "To Kill a Mockingbird", "Harper Lee", 1));
        bookService.addBook(new Book("9780385490818", "The Handmaid's Tale", "Margaret Atwood", 1));

        Member member = new Member("Charlie Brown", "charlie@example.com");
        Member savedMember = bookService.registerMember(member);

        bookService.borrowBook(savedMember.getMemberId(), "9780451524935");
        bookService.borrowBook(savedMember.getMemberId(), "9780316769174");
        bookService.borrowBook(savedMember.getMemberId(), "9780060935467");

        assertThatThrownBy(() -> bookService.borrowBook(savedMember.getMemberId(), "9780385490818"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("3 livres");
    }
}
