package com.utopios.module3.domain.model;

import com.utopios.module3.domain.exception.OrderNotModifiableException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Agrégat racine représentant une commande e-commerce.
 * Aucune dépendance Spring ou JPA — domaine pur.
 * Toutes les règles métier sont encapsulées ici.
 */
public class Order {

    public enum Status {
        DRAFT, CONFIRMED, SHIPPED, CANCELLED
    }

    private final OrderId orderId;
    private final CustomerId customerId;
    private final Email customerEmail;
    private final List<OrderItem> items;
    private Status status;
    private double discountPct;

    private Order(OrderId orderId, CustomerId customerId, Email customerEmail) {
        Objects.requireNonNull(orderId, "L'identifiant de commande ne peut pas être null");
        Objects.requireNonNull(customerId, "L'identifiant client ne peut pas être null");
        Objects.requireNonNull(customerEmail, "L'email client ne peut pas être null");
        this.orderId = orderId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.items = new ArrayList<>();
        this.status = Status.DRAFT;
        this.discountPct = 0.0;
    }

    private Order(OrderId orderId, CustomerId customerId, Email customerEmail,
                  List<OrderItem> items, Status status, double discountPct) {
        Objects.requireNonNull(orderId, "L'identifiant de commande ne peut pas être null");
        Objects.requireNonNull(customerId, "L'identifiant client ne peut pas être null");
        Objects.requireNonNull(customerEmail, "L'email client ne peut pas être null");
        Objects.requireNonNull(items, "La liste des articles ne peut pas être null");
        Objects.requireNonNull(status, "Le statut ne peut pas être null");
        this.orderId = orderId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.items = new ArrayList<>(items);
        this.status = status;
        this.discountPct = discountPct;
    }

    /**
     * Factory method — création d'une nouvelle commande (génère un nouvel ID).
     */
    public static Order create(CustomerId customerId, Email customerEmail) {
        return new Order(OrderId.generate(), customerId, customerEmail);
    }

    /**
     * Factory method — reconstitution depuis la persistence (ID existant).
     * Utilisée par l'OrderMapper uniquement.
     */
    public static Order reconstitute(OrderId orderId, CustomerId customerId, Email customerEmail,
                                     List<OrderItem> items, Status status, double discountPct) {
        return new Order(orderId, customerId, customerEmail, items, status, discountPct);
    }

    /**
     * Ajoute un article à la commande. Interdit si la commande n'est pas en DRAFT.
     */
    public void addItem(OrderItem item) {
        Objects.requireNonNull(item, "L'article ne peut pas être null");
        if (status != Status.DRAFT) {
            throw new OrderNotModifiableException(
                    "Impossible d'ajouter un article : la commande " + orderId
                    + " n'est pas en DRAFT (statut actuel : " + status + ")");
        }
        items.add(item);
    }

    /**
     * Applique une réduction en pourcentage. Entre 0 et 50% exclus.
     * Interdit si la commande est vide ou n'est pas en DRAFT.
     */
    public void applyDiscount(double pct) {
        if (items.isEmpty()) {
            throw new OrderNotModifiableException(
                    "Impossible d'appliquer une réduction : la commande " + orderId + " est vide");
        }
        if (status != Status.DRAFT) {
            throw new OrderNotModifiableException(
                    "Impossible d'appliquer une réduction : la commande " + orderId
                    + " n'est pas en DRAFT (statut actuel : " + status + ")");
        }
        if (pct <= 0 || pct >= 50) {
            throw new IllegalArgumentException(
                    "Le pourcentage de réduction doit être strictement entre 0 et 50, reçu : " + pct);
        }
        this.discountPct = pct;
    }

    /**
     * Calcule le total de la commande en appliquant la réduction éventuelle.
     * Retourne 0.0 si la commande est vide.
     */
    public double calculateTotal() {
        if (items.isEmpty()) {
            return 0.0;
        }
        // On utilise la devise du premier article comme référence
        String currency = items.get(0).unitPrice().currency();
        Money total = items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money.of(0.0, currency), Money::add);

        if (discountPct > 0) {
            total = total.applyDiscount(discountPct);
        }
        return total.toDouble();
    }

    /**
     * Confirme la commande. Interdit si la commande est vide ou déjà confirmée/annulée/expédiée.
     */
    public void confirm() {
        if (items.isEmpty()) {
            throw new OrderNotModifiableException(
                    "Impossible de confirmer la commande " + orderId + " : elle ne contient aucun article");
        }
        if (status != Status.DRAFT) {
            throw new OrderNotModifiableException(
                    "Impossible de confirmer la commande " + orderId
                    + " : statut actuel " + status + " (attendu : DRAFT)");
        }
        this.status = Status.CONFIRMED;
    }

    /**
     * Annule la commande. Interdit si la commande est déjà expédiée.
     */
    public void cancel() {
        if (status == Status.SHIPPED) {
            throw new OrderNotModifiableException(
                    "Impossible d'annuler la commande " + orderId + " : elle a déjà été expédiée");
        }
        this.status = Status.CANCELLED;
    }

    // --- Accesseurs ---

    public OrderId getOrderId() {
        return orderId;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public Email getCustomerEmail() {
        return customerEmail;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Status getStatus() {
        return status;
    }

    public double getDiscountPct() {
        return discountPct;
    }
}
