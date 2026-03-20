package com.bibliotech.domain.exception;

public class BookAlreadyReturnedException extends RuntimeException {
    public BookAlreadyReturnedException(Long borrowingId) {
        super("L'emprunt " + borrowingId + " a deja ete retourne");
    }
}
