package com.utopios.module3.domain.service;

/**
 * Objet de résultat renvoyé après la création ou modification d'une commande.
 * Aucune dépendance Spring ou JPA — domaine pur.
 */
public record OrderResult(String orderId, String status, double total) {
}
