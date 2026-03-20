package com.utopios.module3.integration;

import com.utopios.module3.adapter.out.persistence.JpaOrderRepository;
import com.utopios.module3.adapter.out.persistence.OrderMapper;
import com.utopios.module3.adapter.out.persistence.SpringDataOrderRepository;
import com.utopios.module3.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the persistence layer.
 * Level 3 of the pyramid: @DataJpaTest with H2 in-memory.
 * Simulates what we would do with Testcontainers/PostgreSQL in production.
 *
 * Key point: only the ADAPTER (JpaOrderRepository) is tested here,
 * not the domain logic — that is covered by unit tests.
 */
@DataJpaTest
@Import(OrderMapper.class)
@DisplayName("OrderRepository - JPA Integration Tests (H2)")
class OrderRepositoryIntegrationTest {

    @Autowired
    private SpringDataOrderRepository springDataRepository;

    @Autowired
    private OrderMapper orderMapper;

    private JpaOrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        springDataRepository.deleteAll();
        orderRepository = new JpaOrderRepository(springDataRepository, orderMapper);
    }

    // =========================================================================
    // testSaveAndFindById
    // =========================================================================

    @Test
    @DisplayName("Save an order with items and retrieve it by ID")
    void testSaveAndFindById() {
        Order order = buildOrderWithItems("CLIENT-001", "Keyboard", 89.99, 1);
        OrderId orderId = order.getOrderId();

        orderRepository.save(order);
        Optional<Order> found = orderRepository.findById(orderId);

        assertThat(found).isPresent();
        Order retrieved = found.get();
        assertThat(retrieved.getOrderId()).isEqualTo(orderId);
        assertThat(retrieved.getCustomerId()).isEqualTo(CustomerId.of("CLIENT-001"));
        assertThat(retrieved.getStatus()).isEqualTo(Order.Status.DRAFT);
        assertThat(retrieved.getItems()).hasSize(1);
        assertThat(retrieved.getItems().get(0).name()).isEqualTo("Keyboard");
        assertThat(retrieved.getItems().get(0).unitPrice().toDouble()).isCloseTo(89.99, within(0.01));
        assertThat(retrieved.getItems().get(0).quantity()).isEqualTo(1);
    }

    // =========================================================================
    // testFindByCustomerId
    // =========================================================================

    @Test
    @DisplayName("Filter orders by customer ID — 3 orders for 2 customers")
    void testFindByCustomerId() {
        Order order1 = buildOrderWithItems("CLIENT-A", "Item1", 10.0, 1);
        Order order2 = buildOrderWithItems("CLIENT-A", "Item2", 20.0, 2);
        Order order3 = buildOrderWithItems("CLIENT-B", "Item3", 30.0, 1);

        orderRepository.save(order1);
        orderRepository.save(order2);
        orderRepository.save(order3);

        List<Order> clientAOrders = orderRepository.findByCustomerId(CustomerId.of("CLIENT-A"));
        List<Order> clientBOrders = orderRepository.findByCustomerId(CustomerId.of("CLIENT-B"));
        List<Order> unknownOrders = orderRepository.findByCustomerId(CustomerId.of("CLIENT-Z"));

        assertThat(clientAOrders).hasSize(2);
        assertThat(clientBOrders).hasSize(1);
        assertThat(unknownOrders).isEmpty();
        assertThat(clientAOrders).allMatch(o -> o.getCustomerId().equals(CustomerId.of("CLIENT-A")));
    }

    // =========================================================================
    // testCount
    // =========================================================================

    @Test
    @DisplayName("Count orders in the repository")
    void testCount() {
        assertThat(orderRepository.count()).isEqualTo(0);

        orderRepository.save(buildOrderWithItems("CLIENT-001", "Art1", 10.0, 1));
        orderRepository.save(buildOrderWithItems("CLIENT-002", "Art2", 20.0, 1));
        orderRepository.save(buildOrderWithItems("CLIENT-003", "Art3", 30.0, 1));

        assertThat(orderRepository.count()).isEqualTo(3);
    }

    // =========================================================================
    // testRoundTripMapping
    // =========================================================================

    @Test
    @DisplayName("Round-trip mapping: Domain → JPA → Domain preserves all fields")
    void testRoundTripMapping() {
        Order original = Order.create(CustomerId.of("CLIENT-RT"), Email.of("client-rt@test.com"));
        original.addItem(OrderItem.of(ProductId.of("PROD-001"), "Keyboard", Money.of(89.99, "EUR"), 1));
        original.addItem(OrderItem.of(ProductId.of("PROD-002"), "Mouse", Money.of(49.99, "EUR"), 2));
        original.applyDiscount(15.0);

        orderRepository.save(original);
        Order reloaded = orderRepository.findById(original.getOrderId())
                .orElseThrow(() -> new AssertionError("Order not found after save"));

        assertThat(reloaded.getOrderId()).isEqualTo(original.getOrderId());
        assertThat(reloaded.getCustomerId()).isEqualTo(CustomerId.of("CLIENT-RT"));
        assertThat(reloaded.getStatus()).isEqualTo(Order.Status.DRAFT);
        assertThat(reloaded.getDiscountPct()).isEqualTo(15.0);
        assertThat(reloaded.getItems()).hasSize(2);

        OrderItem item1 = reloaded.getItems().stream()
                .filter(i -> i.productId().equals(ProductId.of("PROD-001")))
                .findFirst().orElseThrow();
        assertThat(item1.name()).isEqualTo("Keyboard");
        assertThat(item1.unitPrice().toDouble()).isCloseTo(89.99, within(0.01));

        OrderItem item2 = reloaded.getItems().stream()
                .filter(i -> i.productId().equals(ProductId.of("PROD-002")))
                .findFirst().orElseThrow();
        assertThat(item2.quantity()).isEqualTo(2);

        assertThat(reloaded.calculateTotal()).isCloseTo(original.calculateTotal(), within(0.01));
    }

    // =========================================================================
    // testDeleteById
    // =========================================================================

    @Test
    @DisplayName("Save then delete — order no longer exists")
    void testDeleteById() {
        Order order = buildOrderWithItems("CLIENT-DEL", "Item", 25.0, 1);
        orderRepository.save(order);
        OrderId orderId = order.getOrderId();

        assertThat(orderRepository.findById(orderId)).isPresent();
        assertThat(orderRepository.count()).isEqualTo(1);

        springDataRepository.deleteById(orderId.value());

        assertThat(orderRepository.findById(orderId)).isEmpty();
        assertThat(orderRepository.count()).isEqualTo(0);
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private Order buildOrderWithItems(String customerId, String productName,
                                       double unitPrice, int quantity) {
        Order order = Order.create(CustomerId.of(customerId), Email.of(customerId + "@test.com"));
        order.addItem(OrderItem.of(
                ProductId.of("PROD-" + productName.hashCode()),
                productName,
                Money.of(unitPrice, "EUR"),
                quantity
        ));
        return order;
    }
}
