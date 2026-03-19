package com.utopios.hexagonal.demos.module2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Demo 04 - Entités, Value Objects et Agrégats
 *
 * Illustre les building blocks du Domain-Driven Design :
 * - Value Objects (immutables, égalité par valeur) avec des records Java
 * - Entités (identité unique, cycle de vie)
 * - Agrégats (racine d'agrégat, invariants métier)
 */
public class Demo04_EntitesVOAggregats {

    // =========================================================================
    // VALUE OBJECTS - Immutables, comparés par valeur, pas d'identité propre
    // =========================================================================

    /**
     * Value Object représentant une devise monétaire.
     * Utilise un record Java pour garantir l'immutabilité.
     */
    public record Currency(String code, String symbol) {

        // Devises prédéfinies
        public static final Currency EUR = new Currency("EUR", "€");
        public static final Currency USD = new Currency("USD", "$");
        public static final Currency GBP = new Currency("GBP", "£");

        public Currency {
            // Validation dans le constructeur compact du record
            Objects.requireNonNull(code, "Le code devise ne peut pas être null");
            Objects.requireNonNull(symbol, "Le symbole devise ne peut pas être null");
            if (code.length() != 3) {
                throw new IllegalArgumentException(
                        "Le code devise doit contenir exactement 3 caractères : " + code);
            }
        }
    }

    /**
     * Value Object représentant un montant monétaire.
     * Encapsule la logique de calcul avec gestion de la devise.
     */
    public record Money(BigDecimal amount, Currency currency) {

        public static final Money ZERO_EUR = new Money(BigDecimal.ZERO, Currency.EUR);

        public Money {
            Objects.requireNonNull(amount, "Le montant ne peut pas être null");
            Objects.requireNonNull(currency, "La devise ne peut pas être null");
            // Arrondir à 2 décimales pour la cohérence
            amount = amount.setScale(2, RoundingMode.HALF_UP);
        }

        /**
         * Additionne deux montants de même devise.
         * Retourne un nouveau Money (immutabilité).
         */
        public Money add(Money other) {
            assertSameCurrency(other);
            return new Money(this.amount.add(other.amount), this.currency);
        }

        /**
         * Soustrait un montant de même devise.
         */
        public Money subtract(Money other) {
            assertSameCurrency(other);
            BigDecimal result = this.amount.subtract(other.amount);
            if (result.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "Le résultat ne peut pas être négatif : " + result);
            }
            return new Money(result, this.currency);
        }

        /**
         * Multiplie le montant par une quantité.
         */
        public Money multiply(int quantity) {
            if (quantity < 0) {
                throw new IllegalArgumentException("La quantité ne peut pas être négative");
            }
            return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)), this.currency);
        }

        public boolean isGreaterThan(Money other) {
            assertSameCurrency(other);
            return this.amount.compareTo(other.amount) > 0;
        }

        private void assertSameCurrency(Money other) {
            if (!this.currency.equals(other.currency)) {
                throw new IllegalArgumentException(
                        "Impossible de combiner des devises différentes : "
                                + this.currency.code() + " et " + other.currency.code());
            }
        }

        @Override
        public String toString() {
            return amount + " " + currency.symbol();
        }
    }

    /**
     * Value Object représentant une adresse email.
     * Valide le format dès la construction.
     */
    public record Email(String value) {

        private static final Pattern EMAIL_PATTERN =
                Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

        public Email {
            Objects.requireNonNull(value, "L'email ne peut pas être null");
            value = value.trim().toLowerCase();
            if (!EMAIL_PATTERN.matcher(value).matches()) {
                throw new IllegalArgumentException("Format d'email invalide : " + value);
            }
        }

        /**
         * Extrait le domaine de l'email.
         */
        public String domain() {
            return value.substring(value.indexOf('@') + 1);
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * Value Object représentant une adresse postale.
     */
    public record Address(String street, String city, String postalCode, String country) {

        public Address {
            Objects.requireNonNull(street, "La rue ne peut pas être null");
            Objects.requireNonNull(city, "La ville ne peut pas être null");
            Objects.requireNonNull(postalCode, "Le code postal ne peut pas être null");
            Objects.requireNonNull(country, "Le pays ne peut pas être null");

            if (street.isBlank()) {
                throw new IllegalArgumentException("La rue ne peut pas être vide");
            }
            if (city.isBlank()) {
                throw new IllegalArgumentException("La ville ne peut pas être vide");
            }
        }

        /**
         * Retourne l'adresse formatée sur une seule ligne.
         */
        public String formatted() {
            return String.format("%s, %s %s, %s", street, postalCode, city, country);
        }

        @Override
        public String toString() {
            return formatted();
        }
    }

    // =========================================================================
    // ENTITÉ - Identifiée par son ID, mutable, cycle de vie
    // =========================================================================

    /**
     * Entité Product : identifiée par son productId.
     * Deux produits avec le même ID sont considérés comme identiques,
     * même si leurs attributs diffèrent (contrairement aux Value Objects).
     */
    public static class Product {

        public enum ProductStatus { DRAFT, ACTIVE, DISCONTINUED }

        private final ProductID productId;
        private String name;
        private String description;
        private Money price;
        private ProductStatus status;

        private Product(ProductID productId, String name, Money price) {
            // Validation des invariants à la construction
            Objects.requireNonNull(productId, "L'identifiant produit ne peut pas être null");
            Objects.requireNonNull(name, "Le nom du produit ne peut pas être null");
            Objects.requireNonNull(price, "Le prix du produit ne peut pas être null");

            if (productId.isBlank()) {
                throw new IllegalArgumentException("L'identifiant produit ne peut pas être vide");
            }
            if (name.isBlank()) {
                throw new IllegalArgumentException("Le nom du produit ne peut pas être vide");
            }

            this.productId = productId;
            this.name = name;
            this.price = price;
            this.status = ProductStatus.DRAFT;
        }

        //Factory method pour créer un produit
        public static Product create(String productId, String name, Money price) {
            return new Product(new ProductID(productId), name, price);
        }

        // --- Méthodes métier de gestion d'état ---

        /**
         * Active le produit. Seul un produit en brouillon peut être activé.
         */
        public void activate() {
            if (this.status != ProductStatus.DRAFT) {
                throw new IllegalStateException(
                        "Seul un produit en brouillon peut être activé. État actuel : " + status);
            }
            this.status = ProductStatus.ACTIVE;
        }

        /**
         * Marque le produit comme discontinué.
         */
        public void discontinue() {
            if (this.status == ProductStatus.DISCONTINUED) {
                throw new IllegalStateException("Le produit est déjà discontinué");
            }
            this.status = ProductStatus.DISCONTINUED;
        }

        /**
         * Met à jour le prix. Interdit pour les produits discontinués.
         */
        public void updatePrice(Money newPrice) {
            if (this.status == ProductStatus.DISCONTINUED) {
                throw new IllegalStateException(
                        "Impossible de modifier le prix d'un produit discontinué");
            }
            Objects.requireNonNull(newPrice, "Le nouveau prix ne peut pas être null");
            this.price = newPrice;
        }

        // --- Égalité basée sur l'identité (ID uniquement) ---

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Product product = (Product) o;
            return productId.equals(product.productId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productId);
        }

        // --- Accesseurs ---

        public ProductID getProductId() { return productId; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public Money getPrice() { return price; }
        public ProductStatus getStatus() { return status; }

        public void setName(String name) { this.name = name; }
        public void setDescription(String description) { this.description = description; }

        @Override
        public String toString() {
            return String.format("Product[id=%s, name=%s, price=%s, status=%s]",
                    productId, name, price, status);
        }
    }

    // =========================================================================
    // AGRÉGAT - Order comme racine d'agrégat avec OrderLine
    // =========================================================================

    /**
     * Value Object représentant une ligne de commande.
     * Appartient à l'agrégat Order et ne peut exister sans lui.
     */
    public record OrderLine(LineID lineId, ProductID productId, String productName,
                            Money unitPrice, int quantity) {

        public OrderLine {
            Objects.requireNonNull(lineId, "L'identifiant de ligne ne peut pas être null");
            Objects.requireNonNull(productId, "L'identifiant produit ne peut pas être null");
            Objects.requireNonNull(productName, "Le nom du produit ne peut pas être null");
            Objects.requireNonNull(unitPrice, "Le prix unitaire ne peut pas être null");
            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "La quantité doit être strictement positive : " + quantity);
            }
        }

        /**
         * Calcule le sous-total de la ligne.
         */
        public Money subtotal() {
            return unitPrice.multiply(quantity);
        }
    }

    /**
     * Agrégat Order : racine d'agrégat qui protège les invariants métier.
     *
     * Règles métier (invariants) :
     * - Une commande doit avoir au moins une ligne pour être confirmée
     * - Le total ne peut pas être négatif
     * - Une commande annulée ne peut plus être modifiée
     * - Pas de doublons de produit dans les lignes
     */
    public static class Order {

        public enum OrderStatus { DRAFT, CONFIRMED, SHIPPED, DELIVERED, CANCELLED }

        private final OrderID orderId;
        private final CustomerID customerId;
        private final List<OrderLine> lines;
        private OrderStatus status;
        private Address shippingAddress;
        private int nextLineNumber;

        private Order(OrderID orderId, CustomerID customerId, Address shippingAddress) {
            Objects.requireNonNull(orderId, "L'identifiant commande ne peut pas être null");
            Objects.requireNonNull(customerId, "L'identifiant client ne peut pas être null");
            Objects.requireNonNull(shippingAddress, "L'adresse de livraison ne peut pas être null");

            this.orderId = orderId;
            this.customerId = customerId;
            this.shippingAddress = shippingAddress;
            this.lines = new ArrayList<>();
            this.status = OrderStatus.DRAFT;
            this.nextLineNumber = 1;
        }

        //Factory method pour créer une commande
        public static Order create(String orderId, String customerId, Address shippingAddress) {
            return new Order(new OrderID(orderId), new CustomerID(customerId), shippingAddress);
        }

        /**
         * Ajoute une ligne de commande. Vérifie les invariants de l'agrégat.
         * Seule la racine d'agrégat peut modifier ses enfants.
         */
        public OrderLine addLine(String productId, String productName,
                                 Money unitPrice, int quantity) {
            assertModifiable();

            // Invariant : pas de doublon de produit
            boolean productExists = lines.stream()
                    .anyMatch(line -> line.productId().equals(productId));
            if (productExists) {
                throw new IllegalArgumentException(
                        "Le produit " + productId + " est déjà dans la commande. "
                                + "Modifiez la ligne existante.");
            }

            LineID lineId = new LineID(orderId + "-L" + (nextLineNumber++));
            OrderLine newLine = new OrderLine(lineId, new ProductID(productId), productName, unitPrice, quantity);
            lines.add(newLine);
            return newLine;
        }

        /**
         * Supprime une ligne de commande par son identifiant.
         */
        public void removeLine(String lineId) {
            assertModifiable();

            boolean removed = lines.removeIf(line -> line.lineId().equals(lineId));
            if (!removed) {
                throw new IllegalArgumentException(
                        "Ligne introuvable : " + lineId);
            }
        }

        /**
         * Calcule le total de la commande en sommant les sous-totaux.
         */
        public Money calculateTotal() {
            if (lines.isEmpty()) {
                return Money.ZERO_EUR;
            }
            return lines.stream()
                    .map(OrderLine::subtotal)
                    .reduce(Money::add)
                    .orElse(Money.ZERO_EUR);
        }

        /**
         * Confirme la commande. Vérifie qu'elle contient au moins une ligne.
         */
        public void confirm() {
            assertModifiable();
            if (lines.isEmpty()) {
                throw new IllegalStateException(
                        "Impossible de confirmer une commande sans lignes");
            }
            this.status = OrderStatus.CONFIRMED;
        }

        /**
         * Annule la commande.
         */
        public void cancel() {
            if (this.status == OrderStatus.SHIPPED || this.status == OrderStatus.DELIVERED) {
                throw new IllegalStateException(
                        "Impossible d'annuler une commande déjà expédiée ou livrée");
            }
            this.status = OrderStatus.CANCELLED;
        }

        private void assertModifiable() {
            if (this.status != OrderStatus.DRAFT) {
                throw new IllegalStateException(
                        "La commande ne peut être modifiée que dans l'état DRAFT. "
                                + "État actuel : " + status);
            }
        }

        // --- Accesseurs (collections retournées en lecture seule) ---

        public OrderID getOrderId() { return orderId; }
        public CustomerID getCustomerId() { return customerId; }
        public OrderStatus getStatus() { return status; }
        public Address getShippingAddress() { return shippingAddress; }

        /**
         * Retourne une copie non modifiable des lignes.
         * L'agrégat protège ses enfants de toute modification externe.
         */
        public List<OrderLine> getLines() {
            return Collections.unmodifiableList(lines);
        }

        public int getLineCount() { return lines.size(); }

        @Override
        public String toString() {
            return String.format("Order[id=%s, client=%s, lignes=%d, total=%s, statut=%s]",
                    orderId, customerId, lines.size(), calculateTotal(), status);
        }
    }

    // =========================================================================
    // DÉMONSTRATION
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=== Demo 04 : Entités, Value Objects et Agrégats ===\n");

        // --- Value Objects ---
        System.out.println("--- Value Objects ---");

        // Money : immutable et comparable par valeur
        Money prix1 = new Money(new BigDecimal("29.99"), Currency.EUR);
        Money prix2 = new Money(new BigDecimal("29.99"), Currency.EUR);
        Money prix3 = new Money(new BigDecimal("15.50"), Currency.EUR);

        System.out.println("Prix 1 : " + prix1);
        System.out.println("Prix 2 : " + prix2);
        System.out.println("Prix 1 == Prix 2 (même valeur) ? " + prix1.equals(prix2)); // true
        System.out.println("Prix 1 + Prix 3 = " + prix1.add(prix3));
        System.out.println("Prix 1 x 3 = " + prix1.multiply(3));

        // Email : validation automatique
        Email email = new Email("  Jean.Dupont@EXAMPLE.COM  ");
        System.out.println("\nEmail normalisé : " + email);
        System.out.println("Domaine : " + email.domain());

        try {
            new Email("pas-un-email");
        } catch (IllegalArgumentException e) {
            System.out.println("Validation email : " + e.getMessage());
        }

        // Address
        Address adresse = new Address("12 rue de la Paix", "Paris", "75002", "France");
        System.out.println("Adresse : " + adresse.formatted());

        // --- Entité Product ---
        System.out.println("\n--- Entité Product ---");

        Product produit1 = new Product("PROD-001", "Clavier mécanique", prix1);
        Product produit2 = new Product("PROD-001", "Clavier mécanique v2",
                new Money(new BigDecimal("39.99"), Currency.EUR));

        System.out.println(produit1);
        System.out.println("Même ID, attributs différents, equals ? " + produit1.equals(produit2)); // true
        System.out.println("Statut initial : " + produit1.getStatus());

        produit1.activate();
        System.out.println("Après activation : " + produit1.getStatus());

        produit1.discontinue();
        System.out.println("Après discontinuation : " + produit1.getStatus());

        try {
            produit1.updatePrice(prix3);
        } catch (IllegalStateException e) {
            System.out.println("Protection d'état : " + e.getMessage());
        }

        // --- Agrégat Order ---
        System.out.println("\n--- Agrégat Order ---");

        Order commande = new Order("CMD-2024-001", "CLIENT-42", adresse);
        System.out.println("Commande créée : " + commande);

        // Ajout de lignes via la racine d'agrégat
        commande.addLine("PROD-001", "Clavier mécanique",
                new Money(new BigDecimal("29.99"), Currency.EUR), 2);
        commande.addLine("PROD-002", "Souris ergonomique",
                new Money(new BigDecimal("49.99"), Currency.EUR), 1);
        commande.addLine("PROD-003", "Tapis de souris XL",
                new Money(new BigDecimal("19.99"), Currency.EUR), 1);

        System.out.println("Après ajout de lignes : " + commande);
        System.out.println("Détail des lignes :");
        for (OrderLine line : commande.getLines()) {
            System.out.printf("  - %s : %s x %d = %s%n",
                    line.productName(), line.unitPrice(), line.quantity(), line.subtotal());
        }

        // Protection contre les doublons
        try {
            commande.addLine("PROD-001", "Clavier mécanique",
                    new Money(new BigDecimal("29.99"), Currency.EUR), 1);
        } catch (IllegalArgumentException e) {
            System.out.println("\nProtection doublon : " + e.getMessage());
        }

        // Suppression d'une ligne
        String lineToRemove = commande.getLines().get(2).lineId();
        commande.removeLine(lineToRemove);
        System.out.println("Après suppression du tapis : " + commande);

        // Confirmation
        commande.confirm();
        System.out.println("Commande confirmée : " + commande.getStatus());

        // Tentative de modification après confirmation
        try {
            commande.addLine("PROD-004", "Câble USB",
                    new Money(new BigDecimal("9.99"), Currency.EUR), 1);
        } catch (IllegalStateException e) {
            System.out.println("Protection post-confirmation : " + e.getMessage());
        }

        // Tentative de confirmer une commande vide
        System.out.println("\n--- Validation des invariants ---");
        Order commandeVide = new Order("CMD-2024-002", "CLIENT-43", adresse);
        try {
            commandeVide.confirm();
        } catch (IllegalStateException e) {
            System.out.println("Invariant commande vide : " + e.getMessage());
        }

        // Mélange de devises interdit
        try {
            Money prixUsd = new Money(new BigDecimal("10.00"), Currency.USD);
            prix1.add(prixUsd);
        } catch (IllegalArgumentException e) {
            System.out.println("Protection devises : " + e.getMessage());
        }

        System.out.println("\n=== Fin de la démo ===");
    }
}
