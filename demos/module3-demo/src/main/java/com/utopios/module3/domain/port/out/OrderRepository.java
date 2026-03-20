package com.utopios.module3.domain.port.out;

import com.utopios.module3.domain.model.CustomerId;
import com.utopios.module3.domain.model.Order;
import com.utopios.module3.domain.model.OrderId;

import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistence des commandes.
 * Aucune dépendance Spring ou JPA — domaine pur.
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(OrderId orderId);

    List<Order> findByCustomerId(CustomerId customerId);

    long count();
}
