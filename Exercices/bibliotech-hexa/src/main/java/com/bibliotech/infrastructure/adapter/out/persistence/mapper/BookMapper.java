package com.bibliotech.infrastructure.adapter.out.persistence.mapper;

import com.bibliotech.domain.model.Book;
import com.bibliotech.domain.vo.Author;
import com.bibliotech.domain.vo.BookTitle;
import com.bibliotech.domain.vo.ISBN;
import com.bibliotech.infrastructure.adapter.out.persistence.entity.BookJpaEntity;

public class BookMapper {

    private BookMapper() {}

    public static Book toDomain(BookJpaEntity entity) {
        return Book.reconstitute(
                new ISBN(entity.getIsbn()),
                new BookTitle(entity.getTitle()),
                new Author(entity.getAuthor()),
                entity.getAvailableCopies()
        );
    }

    public static BookJpaEntity toJpa(Book domain) {
        return new BookJpaEntity(
                domain.getIsbn().value(),
                domain.getTitle().value(),
                domain.getAuthor().value(),
                domain.getAvailableCopies()
        );
    }
}
