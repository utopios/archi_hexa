package com.utopios.module3.adapter.out.persistence;

import com.utopios.module3.domain.model.CustomerId;
import com.utopios.module3.domain.model.Email;
import com.utopios.module3.domain.model.Money;
import com.utopios.module3.domain.model.Order;
import com.utopios.module3.domain.model.OrderId;
import com.utopios.module3.domain.model.OrderItem;
import com.utopios.module3.domain.model.ProductId;

import java.util.List;

/**
 * Mapper bidirectionnel entre le domaine et les entités JPA.
 * Isole complètement le domaine des détails de persistence.
 */
public class OrderMapper {

    /**
     * Convertit un agrégat domaine en entité JPA.
     */
    public OrderJpaEntity toJpaEntity(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity(
                order.getOrderId().value(),
                order.getCustomerId().value(),
                order.getCustomerEmail().value(),
                order.getStatus().name(),
                order.getDiscountPct()
        );

        for (OrderItem item : order.getItems()) {
            OrderItemJpaEntity itemEntity = new OrderItemJpaEntity(
                    item.productId().value(),
                    item.name(),
                    item.unitPrice().amount(),
                    item.unitPrice().currency(),
                    item.quantity()
            );
            entity.addItem(itemEntity);
        }

        return entity;
    }

    /**
     * Convertit une entité JPA en agrégat domaine.
     */
    public Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(this::toItemDomain)
                .toList();

        return Order.reconstitute(
                OrderId.of(entity.getOrderId()),
                CustomerId.of(entity.getCustomerId()),
                Email.of(entity.getCustomerEmail()),
                items,
                Order.Status.valueOf(entity.getStatus()),
                entity.getDiscountPct()
        );
    }

    private OrderItem toItemDomain(OrderItemJpaEntity entity) {
        Money unitPrice = Money.of(entity.getUnitPrice(), entity.getCurrency());
        return OrderItem.of(
                ProductId.of(entity.getProductId()),
                entity.getName(),
                unitPrice,
                entity.getQuantity()
        );
    }
}
