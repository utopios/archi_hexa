package com.utopios.module3.cucumber;

import com.utopios.module3.domain.model.CustomerId;
import com.utopios.module3.domain.model.Order;
import com.utopios.module3.domain.model.OrderId;
import com.utopios.module3.domain.port.out.NotificationPort;
import com.utopios.module3.domain.port.out.OrderRepository;
import com.utopios.module3.domain.service.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared context between all Step Definitions of a scenario.
 *
 * Role in hexagonal architecture:
 * This context plays the role of "wiring" that BeanConfiguration does
 * in production — it assembles ports and services manually.
 * No Spring here — pure Java instantiation.
 */
public class TestContext {

    // =========================================================================
    // In-memory adapters — replace real infrastructure
    // =========================================================================

    /**
     * In-memory repository: simulates JPA without a database.
     * Implements the domain port OrderRepository.
     */
    public static class InMemoryOrderRepository implements OrderRepository {
        private final Map<String, Order> store = new ConcurrentHashMap<>();

        @Override
        public Order save(Order order) {
            store.put(order.getOrderId().value(), order);
            return order;
        }

        @Override
        public Optional<Order> findById(OrderId orderId) {
            return Optional.ofNullable(store.get(orderId.value()));
        }

        @Override
        public List<Order> findByCustomerId(CustomerId customerId) {
            return store.values().stream()
                    .filter(o -> o.getCustomerId().equals(customerId))
                    .toList();
        }

        @Override
        public long count() {
            return store.size();
        }

        public void clear() { store.clear(); }
    }

    /**
     * In-memory notification adapter: records calls for assertions.
     * Simulates email sending without a real SMTP server.
     */
    public static class InMemoryNotificationAdapter implements NotificationPort {
        private final List<String[]> sent = new ArrayList<>();

        @Override
        public void sendConfirmation(String orderId, String customerEmail) {
            sent.add(new String[]{orderId, customerEmail});
        }

        public boolean wasNotifiedFor(String orderId) {
            return sent.stream().anyMatch(n -> n[0].equals(orderId));
        }

        public int count() { return sent.size(); }
        public void clear() { sent.clear(); }
    }

    // =========================================================================
    // Instances (reset before each scenario)
    // =========================================================================

    public final InMemoryOrderRepository orderRepository = new InMemoryOrderRepository();
    public final InMemoryNotificationAdapter notificationAdapter = new InMemoryNotificationAdapter();

    // Services wired manually — no Spring annotation
    public final CreateOrderService createOrderService = new CreateOrderService(orderRepository);
    public final ConfirmOrderService confirmOrderService = new ConfirmOrderService(orderRepository, notificationAdapter);

    // Current scenario state
    public String currentOrderId;
    public String currentCustomerId;
    public String currentCustomerEmail;
    public OrderResult lastResult;
    public Exception capturedException;
}
