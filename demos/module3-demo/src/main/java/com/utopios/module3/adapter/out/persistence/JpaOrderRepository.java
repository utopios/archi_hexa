package com.utopios.module3.adapter.out.persistence;

import com.utopios.module3.domain.model.CustomerId;
import com.utopios.module3.domain.model.Order;
import com.utopios.module3.domain.model.OrderId;
import com.utopios.module3.domain.port.out.OrderRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Adapter de persistence implémentant le port OrderRepository du domaine.
 * Fait le pont entre le domaine et Spring Data JPA.
 */
@Component
public class JpaOrderRepository implements OrderRepository {

    private final SpringDataOrderRepository springDataRepository;
    private final OrderMapper mapper;

    public JpaOrderRepository(SpringDataOrderRepository springDataRepository, OrderMapper mapper) {
        this.springDataRepository = springDataRepository;
        this.mapper = mapper;
    }

    @Override
    public Order save(Order order) {
        // Pour la mise à jour : on supprime d'abord les items orphelins via l'entité existante
        Optional<OrderJpaEntity> existing = springDataRepository.findById(order.getOrderId().value());
        if (existing.isPresent()) {
            OrderJpaEntity existingEntity = existing.get();
            existingEntity.clearItems();
            existingEntity.setStatus(order.getStatus().name());
            existingEntity.setDiscountPct(order.getDiscountPct());
            // Rajoute les items
            for (var item : order.getItems()) {
                OrderItemJpaEntity itemEntity = new OrderItemJpaEntity(
                        item.productId().value(),
                        item.name(),
                        item.unitPrice().amount(),
                        item.unitPrice().currency(),
                        item.quantity()
                );
                existingEntity.addItem(itemEntity);
            }
            OrderJpaEntity saved = springDataRepository.save(existingEntity);
            return mapper.toDomain(saved);
        }

        OrderJpaEntity entity = mapper.toJpaEntity(order);
        OrderJpaEntity saved = springDataRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Order> findById(OrderId orderId) {
        return springDataRepository.findById(orderId.value()).map(mapper::toDomain);
    }

    @Override
    public List<Order> findByCustomerId(CustomerId customerId) {
        return springDataRepository.findByCustomerId(customerId.value())
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long count() {
        return springDataRepository.count();
    }
}
