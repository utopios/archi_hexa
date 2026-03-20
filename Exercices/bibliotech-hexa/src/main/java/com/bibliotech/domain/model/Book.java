package com.bibliotech.domain.model;

import com.bibliotech.domain.exception.BookNotAvailableException;
import com.bibliotech.domain.vo.Author;
import com.bibliotech.domain.vo.BookTitle;
import com.bibliotech.domain.vo.ISBN;

public class Book {

    private final ISBN isbn;
    private final BookTitle title;
    private final Author author;
    private int availableCopies;

    private Book(ISBN isbn, BookTitle title, Author author, int availableCopies) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        if (availableCopies < 0) {
            throw new IllegalArgumentException("Le nombre de copies ne peut pas etre negatif");
        }
        this.availableCopies = availableCopies;
    }

    public static Book of(ISBN isbn, BookTitle title, Author author, int availableCopies) {
        //Enregistrement les événements de création du book
        return new Book(isbn, title, author, availableCopies);
    }

    public static Book reconstitute(ISBN isbn, BookTitle title, Author author, int availableCopies) {
        //Re-construit le book à partir des événements
        return new Book(isbn, title, author, availableCopies);
    }

    public boolean canBeBorrowed() {
        return availableCopies > 0;
    }

    public void decrementStock() {
        if (!canBeBorrowed()) {
            throw new BookNotAvailableException(isbn.value());
        }
        this.availableCopies--;
    }

    public void incrementStock() {
        this.availableCopies++;
    }

    public ISBN getIsbn() { return isbn; }
    public BookTitle getTitle() { return title; }
    public Author getAuthor() { return author; }
    public int getAvailableCopies() { return availableCopies; }
}
