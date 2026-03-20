package com.utopios.module3.domain;

import com.utopios.module3.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitaires du Value Object Money.
 * Niveau 1 de la pyramide : domaine pur, aucune annotation Spring, ultra-rapides.
 */
@DisplayName("Money - Value Object")
class MoneyTest {

    // --- Création ---

    @Test
    @DisplayName("testCreationValide : crée un Money avec montant et devise corrects")
    void testCreationValide() {
        Money money = Money.of(42.50, "EUR");

        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("42.50"));
        assertThat(money.currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("testMontantNegatifInterdit : un montant négatif lève IllegalArgumentException")
    void testMontantNegatifInterdit() {
        assertThatThrownBy(() -> Money.of(-1.0, "EUR"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("négatif");
    }

    @Test
    @DisplayName("testDeviseNulleInterdite : une devise null lève NullPointerException")
    void testDeviseNulleInterdite() {
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- Addition ---

    @Test
    @DisplayName("testAddition : 10 EUR + 5 EUR = 15 EUR")
    void testAddition() {
        Money a = Money.of(10.0, "EUR");
        Money b = Money.of(5.0, "EUR");

        Money result = a.add(b);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(result.currency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("testAdditionDevisesIncompatibles : mélange EUR + USD lève IllegalArgumentException")
    void testAdditionDevisesIncompatibles() {
        Money eur = Money.of(10.0, "EUR");
        Money usd = Money.of(5.0, "USD");

        assertThatThrownBy(() -> eur.add(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("devises");
    }

    // --- Soustraction ---

    @Test
    @DisplayName("testSoustraction : 10 EUR - 3 EUR = 7 EUR")
    void testSoustraction() {
        Money a = Money.of(10.0, "EUR");
        Money b = Money.of(3.0, "EUR");

        Money result = a.subtract(b);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("7.00"));
    }

    @Test
    @DisplayName("testSoustractionResultatNegatifInterdit : 3 EUR - 10 EUR lève IllegalArgumentException")
    void testSoustractionResultatNegatifInterdit() {
        Money a = Money.of(3.0, "EUR");
        Money b = Money.of(10.0, "EUR");

        assertThatThrownBy(() -> a.subtract(b))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("négatif");
    }

    // --- Multiplication ---

    @Test
    @DisplayName("testMultiplication : 15 EUR × 3 = 45 EUR")
    void testMultiplication() {
        Money price = Money.of(15.0, "EUR");

        Money result = price.multiply(3);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(result.currency()).isEqualTo("EUR");
    }

    // --- Réduction ---

    @Test
    @DisplayName("testReduction20Pct : 100 EUR avec 20% de réduction = 80 EUR")
    void testReduction20Pct() {
        Money price = Money.of(100.0, "EUR");

        Money result = price.applyDiscount(20.0);

        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    // --- Immutabilité ---

    @Test
    @DisplayName("testImmutabilite : l'original ne change pas après add()")
    void testImmutabilite() {
        Money original = Money.of(10.0, "EUR");
        Money other = Money.of(5.0, "EUR");

        Money result = original.add(other);

        // L'original reste inchangé
        assertThat(original.amount()).isEqualByComparingTo(new BigDecimal("10.00"));
        // Le résultat est un nouvel objet
        assertThat(result).isNotSameAs(original);
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    // --- Égalité par valeur ---

    @Test
    @DisplayName("testEgaliteParValeur : deux Money(42, EUR) sont equals()")
    void testEgaliteParValeur() {
        Money m1 = Money.of(42.0, "EUR");
        Money m2 = Money.of(42.0, "EUR");

        assertThat(m1).isEqualTo(m2);
        assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
    }
}
