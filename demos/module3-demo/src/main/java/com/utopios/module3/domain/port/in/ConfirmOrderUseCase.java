package com.utopios.module3.domain.port.in;

import com.utopios.module3.domain.model.OrderId;

/**
 * Port d'entrée pour la confirmation de commande.
 * Aucune dépendance Spring ou JPA — domaine pur.
 */
public interface ConfirmOrderUseCase {

    void execute(OrderId orderId);
}
