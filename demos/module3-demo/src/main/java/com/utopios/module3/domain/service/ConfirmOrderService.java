package com.utopios.module3.domain.service;

import com.utopios.module3.domain.exception.OrderNotFoundException;
import com.utopios.module3.domain.model.Order;
import com.utopios.module3.domain.model.OrderId;
import com.utopios.module3.domain.port.in.ConfirmOrderUseCase;
import com.utopios.module3.domain.port.out.NotificationPort;
import com.utopios.module3.domain.port.out.OrderRepository;

import java.util.Objects;

/**
 * Service applicatif pour la confirmation de commandes.
 * Pas d'annotation Spring — câblage via BeanConfiguration.
 */
public class ConfirmOrderService implements ConfirmOrderUseCase {

    private final OrderRepository orderRepository;
    private final NotificationPort notificationPort;

    public ConfirmOrderService(OrderRepository orderRepository, NotificationPort notificationPort) {
        Objects.requireNonNull(orderRepository, "OrderRepository ne peut pas être null");
        Objects.requireNonNull(notificationPort, "NotificationPort ne peut pas être null");
        this.orderRepository = orderRepository;
        this.notificationPort = notificationPort;
    }

    @Override
    public void execute(OrderId orderId) {
        Objects.requireNonNull(orderId, "L'identifiant de commande ne peut pas être null");

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.value()));

        order.confirm();
        orderRepository.save(order);

        notificationPort.sendConfirmation(order.getOrderId().value(), order.getCustomerEmail().value());
    }
}
