package com.utopios.module3.domain.service;

import com.utopios.module3.domain.model.Money;
import com.utopios.module3.domain.model.Order;
import com.utopios.module3.domain.model.OrderItem;
import com.utopios.module3.domain.port.in.CreateOrderUseCase;
import com.utopios.module3.domain.port.out.OrderRepository;

import java.util.Objects;

/**
 * Service applicatif pour la création de commandes.
 * Pas d'annotation Spring — câblage via BeanConfiguration.
 */
public class CreateOrderService implements CreateOrderUseCase {

    private static final String DEFAULT_CURRENCY = "EUR";

    private final OrderRepository orderRepository;

    public CreateOrderService(OrderRepository orderRepository) {
        Objects.requireNonNull(orderRepository, "OrderRepository ne peut pas être null");
        this.orderRepository = orderRepository;
    }

    @Override
    public OrderResult execute(CreateOrderCommand cmd) {
        Objects.requireNonNull(cmd, "La commande ne peut pas être null");

        Order order = Order.create(cmd.customerId(), cmd.customerEmail());

        for (OrderItemCommand itemCmd : cmd.items()) {
            Money unitPrice = Money.of(itemCmd.unitPrice(), DEFAULT_CURRENCY);
            OrderItem item = OrderItem.of(
                    itemCmd.productId(),
                    itemCmd.name(),
                    unitPrice,
                    itemCmd.quantity()
            );
            order.addItem(item);
        }

        Order savedOrder = orderRepository.save(order);

        return new OrderResult(
                savedOrder.getOrderId().value(),
                savedOrder.getStatus().name(),
                savedOrder.calculateTotal()
        );
    }
}
