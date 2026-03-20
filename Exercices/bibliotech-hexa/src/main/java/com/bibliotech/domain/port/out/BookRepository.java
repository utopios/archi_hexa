package com.bibliotech.domain.port.out;

import com.bibliotech.domain.model.Book;
import com.bibliotech.domain.vo.ISBN;
import java.util.List;
import java.util.Optional;

public interface BookRepository {
    Optional<Book> findByIsbn(ISBN isbn);
    List<Book> findByTitleContaining(String keyword);
    List<Book> findAll();
    Book save(Book book);
    void deleteByIsbn(ISBN isbn);
}
