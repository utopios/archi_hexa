package com.bibliotech.application.usecase;

import com.bibliotech.domain.model.Book;
import com.bibliotech.domain.port.in.BookCatalogService;
import com.bibliotech.domain.port.out.BookRepository;
import com.bibliotech.domain.vo.Author;
import com.bibliotech.domain.vo.BookTitle;
import com.bibliotech.domain.vo.ISBN;

import java.util.List;
import java.util.Optional;

public class BookCatalogUseCase implements BookCatalogService {

    private final BookRepository bookRepository;

    public BookCatalogUseCase(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    @Override
    public Book addBook(String isbn, String title, String author, int copies) {
        Book book = Book.of(new ISBN(isbn), new BookTitle(title), new Author(author), copies);
        return bookRepository.save(book);
    }

    @Override
    public Optional<Book> findByIsbn(String isbn) {
        return bookRepository.findByIsbn(new ISBN(isbn));
    }

    @Override
    public List<Book> searchByTitle(String keyword) {
        return bookRepository.findByTitleContaining(keyword);
    }

    @Override
    public List<Book> findAll() {
        return bookRepository.findAll();
    }

    @Override
    public void removeBook(String isbn) {
        bookRepository.deleteByIsbn(new ISBN(isbn));
    }
}
