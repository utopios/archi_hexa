package com.bibliotech.domain.vo;

public record Penalty(double amount) {

    public static final double RATE_PER_DAY = 1.0;
    public static final int LOAN_DURATION_DAYS = 14;

    public Penalty {
        if (amount < 0) {
            throw new IllegalArgumentException("La penalite ne peut pas etre negative");
        }
    }

    public static Penalty zero() {
        return new Penalty(0.0);
    }

    public static Penalty fromLateDays(long daysLate) {
        if (daysLate <= 0) return zero();
        return new Penalty(daysLate * RATE_PER_DAY);
    }

    public Penalty add(Penalty other) {
        return new Penalty(this.amount + other.amount);
    }

    public Penalty subtract(double payment) {
        if (payment > this.amount) {
            throw new IllegalArgumentException("Montant superieur aux penalites dues");
        }
        return new Penalty(this.amount - payment);
    }

    public boolean isUnpaid() {
        return amount > 0;
    }
}
