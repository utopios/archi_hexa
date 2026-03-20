package com.bibliotech.infrastructure.adapter.out.persistence;

import com.bibliotech.domain.model.Book;
import com.bibliotech.domain.port.out.BookRepository;
import com.bibliotech.domain.vo.ISBN;
import com.bibliotech.infrastructure.adapter.out.persistence.entity.BookJpaEntity;
import com.bibliotech.infrastructure.adapter.out.persistence.mapper.BookMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class JpaBookRepository implements BookRepository {

    private final SpringDataBookRepository springDataRepository;

    public JpaBookRepository(SpringDataBookRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<Book> findByIsbn(ISBN isbn) {
        return springDataRepository.findById(isbn.value())
                .map(BookMapper::toDomain);
    }

    @Override
    public List<Book> findByTitleContaining(String keyword) {
        return springDataRepository.findByTitleContainingIgnoreCase(keyword)
                .stream().map(BookMapper::toDomain).toList();
    }

    @Override
    public List<Book> findAll() {
        return springDataRepository.findAll()
                .stream().map(BookMapper::toDomain).toList();
    }

    @Override
    public Book save(Book book) {
        BookJpaEntity saved = springDataRepository.save(BookMapper.toJpa(book));
        return BookMapper.toDomain(saved);
    }

    @Override
    public void deleteByIsbn(ISBN isbn) {
        springDataRepository.deleteById(isbn.value());
    }
}
