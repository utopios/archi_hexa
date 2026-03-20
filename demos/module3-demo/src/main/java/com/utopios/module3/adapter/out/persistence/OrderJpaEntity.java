package com.utopios.module3.adapter.out.persistence;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Entité JPA pour la table ORDERS.
 * Distincte de l'agrégat domaine Order.
 */
@Entity
@Table(name = "ORDERS")
public class OrderJpaEntity {

    @Id
    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String customerEmail;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private double discountPct;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItemJpaEntity> items = new ArrayList<>();

    // Constructeur JPA
    protected OrderJpaEntity() {}

    public OrderJpaEntity(String orderId, String customerId, String customerEmail,
                          String status, double discountPct) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.status = status;
        this.discountPct = discountPct;
    }

    public void addItem(OrderItemJpaEntity item) {
        items.add(item);
        item.setOrder(this);
    }

    public void clearItems() {
        items.forEach(item -> item.setOrder(null));
        items.clear();
    }

    // Getters et Setters

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getDiscountPct() { return discountPct; }
    public void setDiscountPct(double discountPct) { this.discountPct = discountPct; }

    public List<OrderItemJpaEntity> getItems() { return items; }
    public void setItems(List<OrderItemJpaEntity> items) { this.items = items; }
}
