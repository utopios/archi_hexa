package com.utopios.module3.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object immuable représentant une somme monétaire avec sa devise.
 * Aucune dépendance Spring ou JPA — domaine pur.
 */
public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "Le montant ne peut pas être null");
        Objects.requireNonNull(currency, "La devise ne peut pas être null");
        if (currency.isBlank()) {
            throw new IllegalArgumentException("La devise ne peut pas être vide");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le montant ne peut pas être négatif : " + amount);
        }
        // Normalise à 2 décimales
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(double amount, String currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency);
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "La soustraction produirait un montant négatif : " + result);
        }
        return new Money(result, this.currency);
    }

    public Money multiply(int factor) {
        if (factor < 0) {
            throw new IllegalArgumentException("Le facteur de multiplication ne peut pas être négatif : " + factor);
        }
        return new Money(this.amount.multiply(BigDecimal.valueOf(factor)), this.currency);
    }

    /**
     * Applique un pourcentage de réduction (ex: 20.0 pour 20%).
     */
    public Money applyDiscount(double pct) {
        if (pct < 0 || pct >= 100) {
            throw new IllegalArgumentException("Le pourcentage de réduction doit être entre 0 et 100 exclus : " + pct);
        }
        BigDecimal factor = BigDecimal.ONE.subtract(
                BigDecimal.valueOf(pct).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP));
        return new Money(this.amount.multiply(factor).setScale(2, RoundingMode.HALF_UP), this.currency);
    }

    public double toDouble() {
        return amount.doubleValue();
    }

    private void assertSameCurrency(Money other) {
        Objects.requireNonNull(other, "L'autre Money ne peut pas être null");
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Impossibilité de mélanger les devises : " + this.currency + " et " + other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
