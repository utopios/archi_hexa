package com.bibliotech.domain.port.in;

import com.bibliotech.domain.model.Book;
import java.util.List;
import java.util.Optional;

public interface BookCatalogService {
    Book addBook(String isbn, String title, String author, int copies);
    Optional<Book> findByIsbn(String isbn);
    List<Book> searchByTitle(String keyword);
    List<Book> findAll();
    void removeBook(String isbn);
}
