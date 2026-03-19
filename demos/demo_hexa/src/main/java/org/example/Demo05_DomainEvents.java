package org.example;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Demo 05 - Domain Events (Événements du domaine)
 *
 * Illustre le pattern Domain Events :
 * - Les événements capturent ce qui s'est passé dans le domaine
 * - Les agrégats enregistrent des événements lors de changements d'état
 * - Un mécanisme de publication distribue les événements aux abonnés
 * - Découplage entre le producteur et les consommateurs d'événements
 */
public class Demo05_DomainEvents {

    // =========================================================================
    // ÉVÉNEMENTS DU DOMAINE - Interface scellée et implémentations
    // =========================================================================

    /**
     * Interface scellée (sealed) pour tous les événements du domaine.
     * Chaque événement est un fait immuable avec un horodatage.
     */
    public sealed interface DomainEvent
            permits OrderPlaced, OrderShipped, OrderCancelled, PaymentReceived {

        /**
         * Identifiant unique de l'événement.
         */
        String eventId();

        /**
         * Horodatage de l'événement.
         */
        Instant occurredOn();

        /**
         * Identifiant de l'agrégat concerné.
         */
        String aggregateId();
    }

    /**
     * Événement : une commande a été passée.
     */
    public record OrderPlaced(
            String eventId,
            Instant occurredOn,
            String aggregateId,
            String customerId,
            BigDecimal totalAmount
    ) implements DomainEvent {

        public OrderPlaced(String orderId, String customerId, BigDecimal totalAmount) {
            this(UUID.randomUUID().toString(), Instant.now(), orderId, customerId, totalAmount);
        }
    }

    /**
     * Événement : une commande a été expédiée.
     */
    public record OrderShipped(
            String eventId,
            Instant occurredOn,
            String aggregateId,
            String trackingNumber
    ) implements DomainEvent {

        public OrderShipped(String orderId, String trackingNumber) {
            this(UUID.randomUUID().toString(), Instant.now(), orderId, trackingNumber);
        }
    }

    /**
     * Événement : une commande a été annulée.
     */
    public record OrderCancelled(
            String eventId,
            Instant occurredOn,
            String aggregateId,
            String reason
    ) implements DomainEvent {

        public OrderCancelled(String orderId, String reason) {
            this(UUID.randomUUID().toString(), Instant.now(), orderId, reason);
        }
    }

    /**
     * Événement : un paiement a été reçu.
     */
    public record PaymentReceived(
            String eventId,
            Instant occurredOn,
            String aggregateId,
            String paymentId,
            BigDecimal amount
    ) implements DomainEvent {

        public PaymentReceived(String orderId, String paymentId, BigDecimal amount) {
            this(UUID.randomUUID().toString(), Instant.now(), orderId, paymentId, amount);
        }
    }

    // =========================================================================
    // RACINE D'AGRÉGAT DE BASE - Gestion des événements
    // =========================================================================

    /**
     * Classe de base pour les racines d'agrégats.
     * Fournit le mécanisme d'enregistrement et de récupération des événements.
     *
     * Les événements sont collectés pendant l'exécution des méthodes métier
     * puis récupérés et publiés par la couche application (service).
     */
    public static abstract class AggregateRoot {

        private final List<DomainEvent> domainEvents = new ArrayList<>();

        /**
         * Enregistre un événement. Appelé par les méthodes métier de l'agrégat.
         * L'événement n'est pas publié immédiatement, juste enregistré.
         */
        protected void registerEvent(DomainEvent event) {
            Objects.requireNonNull(event, "L'événement ne peut pas être null");
            domainEvents.add(event);
        }

        /**
         * Récupère et vide la liste des événements en attente.
         * Appelé par le service applicatif après la persistance de l'agrégat.
         *
         * @return liste des événements enregistrés depuis le dernier appel
         */
        public List<DomainEvent> pullEvents() {
            List<DomainEvent> events = new ArrayList<>(domainEvents);
            domainEvents.clear();
            return Collections.unmodifiableList(events);
        }

        /**
         * Consulte les événements en attente sans les vider.
         */
        public List<DomainEvent> peekEvents() {
            return Collections.unmodifiableList(domainEvents);
        }
    }

    // =========================================================================
    // AGRÉGAT ORDER - Publie des événements lors de changements d'état
    // =========================================================================

    /**
     * Agrégat Order qui enregistre des événements métier
     * à chaque transition d'état significative.
     */
    public static class Order extends AggregateRoot {

        public enum Status { DRAFT, PLACED, PAID, SHIPPED, CANCELLED }

        private final String orderId;
        private final String customerId;
        private BigDecimal totalAmount;
        private Status status;
        private String trackingNumber;

        public Order(String orderId, String customerId) {
            this.orderId = Objects.requireNonNull(orderId);
            this.customerId = Objects.requireNonNull(customerId);
            this.totalAmount = BigDecimal.ZERO;
            this.status = Status.DRAFT;
        }

        /**
         * Passe la commande. Transition DRAFT → PLACED.
         * Enregistre un événement OrderPlaced.
         */
        public void place(BigDecimal amount) {
            if (this.status != Status.DRAFT) {
                throw new IllegalStateException(
                        "Seule une commande en brouillon peut être passée. État : " + status);
            }
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Le montant doit être positif");
            }

            this.totalAmount = amount;
            this.status = Status.PLACED;

            // Enregistrement de l'événement métier
            registerEvent(new OrderPlaced(orderId, customerId, amount));
        }

        /**
         * Enregistre le paiement. Transition PLACED → PAID.
         * Enregistre un événement PaymentReceived.
         */
        public void receivePayment(String paymentId, BigDecimal amount) {
            if (this.status != Status.PLACED) {
                throw new IllegalStateException(
                        "Le paiement n'est accepté que pour les commandes passées. État : " + status);
            }
            if (amount.compareTo(this.totalAmount) < 0) {
                throw new IllegalArgumentException(
                        "Montant insuffisant. Attendu : " + totalAmount + ", reçu : " + amount);
            }

            this.status = Status.PAID;

            registerEvent(new PaymentReceived(orderId, paymentId, amount));
        }

        /**
         * Expédie la commande. Transition PAID → SHIPPED.
         * Enregistre un événement OrderShipped.
         */
        public void ship(String trackingNumber) {
            if (this.status != Status.PAID) {
                throw new IllegalStateException(
                        "Seule une commande payée peut être expédiée. État : " + status);
            }
            Objects.requireNonNull(trackingNumber, "Le numéro de suivi est obligatoire");

            this.trackingNumber = trackingNumber;
            this.status = Status.SHIPPED;

            registerEvent(new OrderShipped(orderId, trackingNumber));
        }

        /**
         * Annule la commande. Possible depuis DRAFT ou PLACED.
         * Enregistre un événement OrderCancelled.
         */
        public void cancel(String reason) {
            if (this.status == Status.SHIPPED) {
                throw new IllegalStateException("Impossible d'annuler une commande expédiée");
            }
            Objects.requireNonNull(reason, "La raison d'annulation est obligatoire");

            this.status = Status.CANCELLED;

            registerEvent(new OrderCancelled(orderId, reason));
        }

        // --- Accesseurs ---

        public String getOrderId() { return orderId; }
        public String getCustomerId() { return customerId; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public Status getStatus() { return status; }
        public String getTrackingNumber() { return trackingNumber; }

        @Override
        public String toString() {
            return String.format("Order[id=%s, client=%s, montant=%s, statut=%s]",
                    orderId, customerId, totalAmount, status);
        }
    }

    // =========================================================================
    // PORT ET ADAPTATEUR POUR LA PUBLICATION D'ÉVÉNEMENTS
    // =========================================================================

    /**
     * Port secondaire (interface) pour la publication d'événements.
     * Défini dans le domaine, implémenté par l'infrastructure.
     */
    @FunctionalInterface
    public interface DomainEventPublisher {
        void publish(DomainEvent event);
    }

    /**
     * Adaptateur simple en mémoire pour la démo.
     * En production, ce serait Kafka, RabbitMQ, etc.
     */
    public static class InMemoryDomainEventPublisher implements DomainEventPublisher {

        // Abonnés par type d'événement
        private final Map<Class<? extends DomainEvent>, List<Consumer<DomainEvent>>> subscribers =
                new HashMap<>();

        // Historique de tous les événements publiés
        private final List<DomainEvent> publishedEvents = new CopyOnWriteArrayList<>();

        /**
         * Abonne un consommateur à un type d'événement spécifique.
         */
        @SuppressWarnings("unchecked")
        public <T extends DomainEvent> void subscribe(Class<T> eventType, Consumer<T> handler) {
            subscribers.computeIfAbsent(eventType, k -> new ArrayList<>())
                    .add(event -> handler.accept((T) event));
        }

        /**
         * Publie un événement à tous les abonnés intéressés.
         */
        @Override
        public void publish(DomainEvent event) {
            publishedEvents.add(event);
            System.out.printf("  [PUBLISH] %s (id=%s, agrégat=%s)%n",
                    event.getClass().getSimpleName(), event.eventId().substring(0, 8),
                    event.aggregateId());

            // Notifier les abonnés du type exact
            List<Consumer<DomainEvent>> handlers = subscribers.get(event.getClass());
            if (handlers != null) {
                handlers.forEach(handler -> handler.accept(event));
            }
        }

        /**
         * Publie tous les événements en attente d'un agrégat.
         */
        public void publishAll(List<DomainEvent> events) {
            events.forEach(this::publish);
        }

        public List<DomainEvent> getPublishedEvents() {
            return Collections.unmodifiableList(publishedEvents);
        }
    }

    // =========================================================================
    // SERVICE APPLICATIF - Orchestre l'agrégat et la publication
    // =========================================================================

    /**
     * Service applicatif qui orchestre le flux :
     * 1. Exécuter la logique métier sur l'agrégat
     * 2. Persister l'agrégat (simulé ici)
     * 3. Publier les événements collectés
     */
    public static class OrderApplicationService {

        private final DomainEventPublisher eventPublisher;
        private final Map<String, Order> orderStore = new HashMap<>(); // simulation de persistance

        public OrderApplicationService(DomainEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
        }

        public Order placeOrder(String orderId, String customerId, BigDecimal amount) {
            Order order = new Order(orderId, customerId);
            order.place(amount);

            // Persister l'agrégat (simulé)
            orderStore.put(orderId, order);

            // Récupérer et publier les événements
            List<DomainEvent> events = order.pullEvents();
            events.forEach(eventPublisher::publish);

            return order;
        }

        public void payOrder(String orderId, String paymentId, BigDecimal amount) {
            Order order = orderStore.get(orderId);
            if (order == null) {
                throw new IllegalArgumentException("Commande introuvable : " + orderId);
            }

            order.receivePayment(paymentId, amount);

            // Publier les événements après la persistance
            order.pullEvents().forEach(eventPublisher::publish);
        }

        public void shipOrder(String orderId, String trackingNumber) {
            Order order = orderStore.get(orderId);
            if (order == null) {
                throw new IllegalArgumentException("Commande introuvable : " + orderId);
            }

            order.ship(trackingNumber);
            order.pullEvents().forEach(eventPublisher::publish);
        }
    }

    // =========================================================================
    // DÉMONSTRATION
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=== Demo 05 : Domain Events ===\n");

        // --- Configuration du publisher avec des abonnés ---
        InMemoryDomainEventPublisher publisher = new InMemoryDomainEventPublisher();

        // Abonné : envoi d'email de confirmation (simulé)
        publisher.subscribe(OrderPlaced.class, event ->
                System.out.printf("  [EMAIL] Confirmation envoyée au client %s "
                        + "pour la commande %s (montant: %s€)%n",
                        event.customerId(), event.aggregateId(), event.totalAmount()));

        // Abonné : notification au service de stock (simulé)
        publisher.subscribe(OrderPlaced.class, event ->
                System.out.printf("  [STOCK] Réservation de stock pour la commande %s%n",
                        event.aggregateId()));

        // Abonné : notification de paiement
        publisher.subscribe(PaymentReceived.class, event ->
                System.out.printf("  [COMPTA] Paiement %s de %s€ enregistré pour commande %s%n",
                        event.paymentId(), event.amount(), event.aggregateId()));

        // Abonné : notification d'expédition
        publisher.subscribe(OrderShipped.class, event ->
                System.out.printf("  [LOGISTIQUE] Commande %s expédiée, suivi: %s%n",
                        event.aggregateId(), event.trackingNumber()));

        // --- Service applicatif ---
        OrderApplicationService service = new OrderApplicationService(publisher);

        // Scénario : cycle de vie complet d'une commande
        System.out.println("1. Passage de la commande :");
        Order order = service.placeOrder("CMD-001", "CLIENT-42", new BigDecimal("149.99"));
        System.out.println("   → " + order + "\n");

        System.out.println("2. Paiement reçu :");
        service.payOrder("CMD-001", "PAY-ABC-123", new BigDecimal("149.99"));
        System.out.println();

        System.out.println("3. Expédition :");
        service.shipOrder("CMD-001", "COLISSIMO-FR-78945612");
        System.out.println();

        // --- Pattern matching sur les événements (Java 17+) ---
        System.out.println("--- Historique des événements ---");
        List<DomainEvent> historique = publisher.getPublishedEvents();
        System.out.println("Nombre total d'événements : " + historique.size());

        for (DomainEvent event : historique) {
            String description;
            if (event instanceof OrderPlaced e) {
                description = String.format(
                        "Commande passée par %s pour %s€", e.customerId(), e.totalAmount());
            } else if (event instanceof PaymentReceived e) {
                description = String.format(
                        "Paiement de %s€ reçu (ref: %s)", e.amount(), e.paymentId());
            } else if (event instanceof OrderShipped e) {
                description = String.format(
                        "Commande expédiée (suivi: %s)", e.trackingNumber());
            } else if (event instanceof OrderCancelled e) {
                description = String.format(
                        "Commande annulée (raison: %s)", e.reason());
            } else {
                description = "Événement inconnu";
            }
            System.out.printf("  [%s] %s - %s%n",
                    event.occurredOn(), event.getClass().getSimpleName(), description);
        }

        // --- Démonstration de pullEvents() ---
        System.out.println("\n--- pullEvents() vide la liste ---");
        Order orderDirect = new Order("CMD-002", "CLIENT-99");
        orderDirect.place(new BigDecimal("59.99"));
        orderDirect.cancel("Changement d'avis");

        List<DomainEvent> events1 = orderDirect.pullEvents();
        System.out.println("Premier appel pullEvents() : " + events1.size() + " événement(s)");
        events1.forEach(e -> System.out.println("  - " + e.getClass().getSimpleName()));

        List<DomainEvent> events2 = orderDirect.pullEvents();
        System.out.println("Second appel pullEvents() : " + events2.size() + " événement(s) (vidé)");

        System.out.println("\n=== Fin de la démo ===");
    }
}
