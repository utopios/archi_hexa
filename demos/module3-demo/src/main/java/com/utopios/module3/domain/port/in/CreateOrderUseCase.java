package com.utopios.module3.domain.port.in;

import com.utopios.module3.domain.service.CreateOrderCommand;
import com.utopios.module3.domain.service.OrderResult;

/**
 * Port d'entrée pour la création de commande.
 * Aucune dépendance Spring ou JPA — domaine pur.
 */
public interface CreateOrderUseCase {

    OrderResult execute(CreateOrderCommand cmd);
}
