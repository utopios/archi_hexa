package com.bibliotech.domain.exception;

public class UnpaidPenaltiesException extends RuntimeException {
    private final double amount;

    public UnpaidPenaltiesException(String memberId, double amount) {
        super("Le membre " + memberId + " a des penalites impayees: " + amount + " EUR");
        this.amount = amount;
    }

    public double getAmount() { return amount; }
}
