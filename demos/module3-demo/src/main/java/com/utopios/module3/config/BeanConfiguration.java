package com.utopios.module3.config;

import com.utopios.module3.adapter.out.persistence.OrderMapper;
import com.utopios.module3.domain.port.in.ConfirmOrderUseCase;
import com.utopios.module3.domain.port.in.CreateOrderUseCase;
import com.utopios.module3.domain.port.out.NotificationPort;
import com.utopios.module3.domain.port.out.OrderRepository;
import com.utopios.module3.domain.service.ConfirmOrderService;
import com.utopios.module3.domain.service.CreateOrderService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration Spring qui câble les services du domaine avec les adapters.
 * Le domaine ne connaît pas Spring — c'est ce fichier qui fait la glue.
 */
@Configuration
public class BeanConfiguration {

    @Bean
    public OrderMapper orderMapper() {
        return new OrderMapper();
    }

    @Bean
    public CreateOrderUseCase createOrderUseCase(OrderRepository orderRepository) {
        return new CreateOrderService(orderRepository);
    }

    @Bean
    public ConfirmOrderUseCase confirmOrderUseCase(OrderRepository orderRepository,
                                                    NotificationPort notificationPort) {
        return new ConfirmOrderService(orderRepository, notificationPort);
    }
}
