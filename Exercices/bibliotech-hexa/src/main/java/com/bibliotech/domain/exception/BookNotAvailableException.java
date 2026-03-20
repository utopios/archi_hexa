package com.bibliotech.domain.exception;

public class BookNotAvailableException extends RuntimeException {
    public BookNotAvailableException(String isbn) {
        super("Aucune copie disponible pour le livre: " + isbn);
    }
}
