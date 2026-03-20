package com.bibliotech.domain.exception;

public class BorrowingLimitReachedException extends RuntimeException {
    public BorrowingLimitReachedException(String memberId) {
        super("Le membre " + memberId + " a atteint la limite de 3 emprunts");
    }
}
