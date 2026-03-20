package com.bibliotech.infrastructure.adapter.in.rest.dto;

import com.bibliotech.domain.model.Book;

public record BookResponse(
        String isbn,
        String title,
        String author,
        int availableCopies
) {
    public static BookResponse from(Book book) {
        return new BookResponse(
                book.getIsbn().value(),
                book.getTitle().value(),
                book.getAuthor().value(),
                book.getAvailableCopies()
        );
    }
}
