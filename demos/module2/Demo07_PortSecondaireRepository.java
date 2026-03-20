package com.utopios.hexagonal.demos.module2;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Demo 07 - Port Secondaire et Adaptateurs Repository
 *
 * Illustre le pattern Ports & Adapters côté "driven" (secondaire) :
 * - Le PORT SECONDAIRE (OrderRepository) est défini dans le domaine
 * - Les ADAPTATEURS SECONDAIRES implémentent ce port pour différentes technologies
 * - Le SERVICE APPLICATIF utilise le port sans connaître l'implémentation
 * - Un MAPPER convertit entre le modèle domaine et le modèle de persistance
 *
 * Direction de la dépendance (Dependency Inversion) :
 *   [Service Applicatif] → [OrderRepository (port/interface)] ← [InMemoryAdapter / JpaAdapter]
 *                                                                         ↑
 *   Le domaine définit l'interface, l'infrastructure l'implémente.
 */
public class Demo07_PortSecondaireRepository {

    // =========================================================================
    // MODÈLE DU DOMAINE
    // =========================================================================

    /**
     * Entité Order du domaine - modèle riche avec logique métier.
     */
    public static class Order {
        public enum Status { DRAFT, PLACED, CONFIRMED, SHIPPED, CANCELLED }

        private final String orderId;
        private final String customerId;
        private final List<OrderItem> items;
        private BigDecimal totalAmount;
        private Status status;
        private final Instant createdAt;
        private Instant updatedAt;

        public Order(String orderId, String customerId) {
            this.orderId = Objects.requireNonNull(orderId);
            this.customerId = Objects.requireNonNull(customerId);
            this.items = new ArrayList<>();
            this.totalAmount = BigDecimal.ZERO;
            this.status = Status.DRAFT;
            this.createdAt = Instant.now();
            this.updatedAt = this.createdAt;
        }

        // Constructeur de reconstitution (utilisé par les mappers)
        public Order(String orderId, String customerId, List<OrderItem> items,
                     BigDecimal totalAmount, Status status, Instant createdAt, Instant updatedAt) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.items = new ArrayList<>(items);
            this.totalAmount = totalAmount;
            this.status = status;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public void addItem(String productId, String productName, int quantity, BigDecimal unitPrice) {
            if (status != Status.DRAFT) {
                throw new IllegalStateException("Modification impossible, statut : " + status);
            }
            items.add(new OrderItem(productId, productName, quantity, unitPrice));
            recalculateTotal();
            this.updatedAt = Instant.now();
        }

        public void place() {
            if (items.isEmpty()) {
                throw new IllegalStateException("Impossible de passer une commande vide");
            }
            this.status = Status.PLACED;
            this.updatedAt = Instant.now();
        }

        public void confirm() {
            if (status != Status.PLACED) {
                throw new IllegalStateException("Seule une commande passée peut être confirmée");
            }
            this.status = Status.CONFIRMED;
            this.updatedAt = Instant.now();
        }

        private void recalculateTotal() {
            this.totalAmount = items.stream()
                    .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // Accesseurs
        public String getOrderId() { return orderId; }
        public String getCustomerId() { return customerId; }
        public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public Status getStatus() { return status; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }

        @Override
        public String toString() {
            return String.format("Order[id=%s, client=%s, articles=%d, total=%s, statut=%s]",
                    orderId, customerId, items.size(), totalAmount, status);
        }
    }

    /**
     * Value Object pour un article de commande.
     */
    public record OrderItem(String productId, String productName,
                            int quantity, BigDecimal unitPrice) {
        public OrderItem {
            Objects.requireNonNull(productId);
            Objects.requireNonNull(productName);
            if (quantity <= 0) throw new IllegalArgumentException("Quantité invalide");
            Objects.requireNonNull(unitPrice);
        }
    }

    // =========================================================================
    // PORT SECONDAIRE - Défini dans la couche domaine
    // =========================================================================

    /**
     * Port secondaire : interface de persistance définie par le domaine.
     *
     * Le domaine DÉFINIT ce dont il a besoin (quelles opérations),
     * sans savoir COMMENT ce sera implémenté (quelle base de données).
     *
     * C'est l'inversion de dépendance (DIP) : le domaine ne dépend
     * pas de l'infrastructure, c'est l'infrastructure qui dépend du domaine.
     */
    public interface OrderRepository {

        /**
         * Sauvegarde une commande (création ou mise à jour).
         */
        void save(Order order);

        /**
         * Recherche une commande par son identifiant.
         */
        Optional<Order> findById(String orderId);

        /**
         * Recherche toutes les commandes d'un client.
         */
        List<Order> findByCustomerId(String customerId);

        /**
         * Recherche les commandes par statut.
         */
        List<Order> findByStatus(Order.Status status);

        /**
         * Supprime une commande par son identifiant.
         */
        void deleteById(String orderId);

        /**
         * Vérifie l'existence d'une commande.
         */
        boolean existsById(String orderId);

        /**
         * Compte le nombre total de commandes.
         */
        long count();
    }

    // =========================================================================
    // ADAPTATEUR 1 : InMemory (pour les tests)
    // =========================================================================

    /**
     * Adaptateur en mémoire pour les tests unitaires et d'intégration.
     * Simple, rapide, sans dépendance externe.
     */
    public static class InMemoryOrderRepository implements OrderRepository {

        private final Map<String, Order> store = new ConcurrentHashMap<>();

        @Override
        public void save(Order order) {
            store.put(order.getOrderId(), order);
        }

        @Override
        public Optional<Order> findById(String orderId) {
            return Optional.ofNullable(store.get(orderId));
        }

        @Override
        public List<Order> findByCustomerId(String customerId) {
            return store.values().stream()
                    .filter(o -> o.getCustomerId().equals(customerId))
                    .toList();
        }

        @Override
        public List<Order> findByStatus(Order.Status status) {
            return store.values().stream()
                    .filter(o -> o.getStatus() == status)
                    .toList();
        }

        @Override
        public void deleteById(String orderId) {
            store.remove(orderId);
        }

        @Override
        public boolean existsById(String orderId) {
            return store.containsKey(orderId);
        }

        @Override
        public long count() {
            return store.size();
        }

        @Override
        public String toString() {
            return "InMemoryOrderRepository[" + store.size() + " commande(s)]";
        }
    }

    // =========================================================================
    // MODÈLE JPA (entité de persistance) - Séparé du modèle domaine
    // =========================================================================

    /**
     * Entité JPA : représentation en base de données.
     * Structure plate, adaptée au modèle relationnel.
     *
     * IMPORTANT : Ce n'est PAS l'entité du domaine.
     * Les deux modèles évoluent indépendamment.
     *
     * Pseudo-annotations JPA commentées :
     * // @Entity
     * // @Table(name = "orders")
     */
    public static class OrderJpaEntity {
        // @Id
        private String id;
        // @Column(name = "customer_id")
        private String customerId;
        // @Column(name = "total_amount")
        private BigDecimal totalAmount;
        // @Column(name = "status")
        private String status;
        // @Column(name = "created_at")
        private LocalDateTime createdAt;
        // @Column(name = "updated_at")
        private LocalDateTime updatedAt;

        // @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
        private List<OrderItemJpaEntity> items = new ArrayList<>();

        // Constructeur par défaut requis par JPA
        public OrderJpaEntity() {}

        // Getters et setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public LocalDateTime getCreatedAt() { return createdAt; }
        public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
        public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
        public List<OrderItemJpaEntity> getItems() { return items; }
        public void setItems(List<OrderItemJpaEntity> items) { this.items = items; }

        @Override
        public String toString() {
            return String.format("OrderJpaEntity[id=%s, status=%s, items=%d]",
                    id, status, items.size());
        }
    }

    /**
     * Entité JPA pour les lignes de commande.
     * // @Entity
     * // @Table(name = "order_items")
     */
    public static class OrderItemJpaEntity {
        private String productId;
        private String productName;
        private int quantity;
        private BigDecimal unitPrice;

        public OrderItemJpaEntity() {}

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }
        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    }

    // =========================================================================
    // MAPPER - Conversion entre domaine et persistance
    // =========================================================================

    /**
     * Mapper responsable de la conversion entre le modèle domaine et le modèle JPA.
     *
     * Le mapper vit dans la couche infrastructure (avec l'adaptateur).
     * Il connaît les deux modèles et sait comment les convertir.
     */
    public static class OrderMapper {

        /**
         * Convertit une entité domaine en entité JPA pour la persistance.
         */
        public static OrderJpaEntity toJpaEntity(Order domain) {
            OrderJpaEntity jpa = new OrderJpaEntity();
            jpa.setId(domain.getOrderId());
            jpa.setCustomerId(domain.getCustomerId());
            jpa.setTotalAmount(domain.getTotalAmount());
            jpa.setStatus(domain.getStatus().name());
            jpa.setCreatedAt(LocalDateTime.ofInstant(
                    domain.getCreatedAt(), java.time.ZoneId.systemDefault()));
            jpa.setUpdatedAt(LocalDateTime.ofInstant(
                    domain.getUpdatedAt(), java.time.ZoneId.systemDefault()));

            List<OrderItemJpaEntity> jpaItems = domain.getItems().stream()
                    .map(OrderMapper::toJpaEntity)
                    .collect(Collectors.toList());
            jpa.setItems(jpaItems);

            return jpa;
        }

        /**
         * Convertit un article domaine en entité JPA.
         */
        public static OrderItemJpaEntity toJpaEntity(OrderItem domain) {
            OrderItemJpaEntity jpa = new OrderItemJpaEntity();
            jpa.setProductId(domain.productId());
            jpa.setProductName(domain.productName());
            jpa.setQuantity(domain.quantity());
            jpa.setUnitPrice(domain.unitPrice());
            return jpa;
        }

        /**
         * Reconstitue une entité domaine à partir d'une entité JPA.
         * Utilise le constructeur de reconstitution (pas de validation métier).
         */
        public static Order toDomain(OrderJpaEntity jpa) {
            List<OrderItem> items = jpa.getItems().stream()
                    .map(OrderMapper::toDomain)
                    .toList();

            return new Order(
                    jpa.getId(),
                    jpa.getCustomerId(),
                    items,
                    jpa.getTotalAmount(),
                    Order.Status.valueOf(jpa.getStatus()),
                    jpa.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                    jpa.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()
            );
        }

        /**
         * Reconstitue un article domaine à partir d'une entité JPA.
         */
        public static OrderItem toDomain(OrderItemJpaEntity jpa) {
            return new OrderItem(
                    jpa.getProductId(),
                    jpa.getProductName(),
                    jpa.getQuantity(),
                    jpa.getUnitPrice()
            );
        }
    }

    // =========================================================================
    // ADAPTATEUR 2 : JPA (pour la production)
    // =========================================================================

    /**
     * Adaptateur JPA pour la persistance en base de données relationnelle.
     *
     * En production, cet adaptateur utiliserait un vrai EntityManager JPA.
     * Ici, nous simulons les opérations JPA avec une Map.
     *
     * Points importants :
     * - Le mapper convertit entre domaine et persistance
     * - L'adaptateur ne connaît que le modèle JPA en interne
     * - L'extérieur ne voit que le modèle domaine (via le port)
     */
    public static class JpaOrderRepository implements OrderRepository {

        // Simulation de l'EntityManager JPA
        private final Map<String, OrderJpaEntity> database = new ConcurrentHashMap<>();

        @Override
        public void save(Order order) {
            System.out.println("    [JPA] Conversion domaine → JPA");
            OrderJpaEntity jpaEntity = OrderMapper.toJpaEntity(order);

            System.out.println("    [JPA] Persistance : " + jpaEntity);
            // En vrai : entityManager.persist(jpaEntity) ou entityManager.merge(jpaEntity)
            database.put(jpaEntity.getId(), jpaEntity);

            System.out.println("    [JPA] Sauvegardé en base");
        }

        @Override
        public Optional<Order> findById(String orderId) {
            System.out.println("    [JPA] Recherche par ID : " + orderId);
            // En vrai : entityManager.find(OrderJpaEntity.class, orderId)
            OrderJpaEntity jpaEntity = database.get(orderId);

            if (jpaEntity != null) {
                System.out.println("    [JPA] Trouvé, conversion JPA → domaine");
                return Optional.of(OrderMapper.toDomain(jpaEntity));
            }
            System.out.println("    [JPA] Non trouvé");
            return Optional.empty();
        }

        @Override
        public List<Order> findByCustomerId(String customerId) {
            System.out.println("    [JPA] Recherche par client : " + customerId);
            // En vrai : requête JPQL ou Criteria API
            return database.values().stream()
                    .filter(e -> e.getCustomerId().equals(customerId))
                    .map(OrderMapper::toDomain)
                    .toList();
        }

        @Override
        public List<Order> findByStatus(Order.Status status) {
            System.out.println("    [JPA] Recherche par statut : " + status);
            return database.values().stream()
                    .filter(e -> e.getStatus().equals(status.name()))
                    .map(OrderMapper::toDomain)
                    .toList();
        }

        @Override
        public void deleteById(String orderId) {
            System.out.println("    [JPA] Suppression : " + orderId);
            database.remove(orderId);
        }

        @Override
        public boolean existsById(String orderId) {
            return database.containsKey(orderId);
        }

        @Override
        public long count() {
            return database.size();
        }

        @Override
        public String toString() {
            return "JpaOrderRepository[" + database.size() + " commande(s) en base]";
        }
    }

    // =========================================================================
    // SERVICE APPLICATIF - Utilise le port sans connaître l'implémentation
    // =========================================================================

    /**
     * Service applicatif qui dépend du PORT (interface OrderRepository),
     * pas d'une implémentation concrète.
     *
     * Grâce à l'injection de dépendances, on peut substituer
     * l'adaptateur InMemory (tests) par l'adaptateur JPA (production)
     * sans modifier une seule ligne du service.
     */
    public static class OrderApplicationService {

        private final OrderRepository orderRepository; // ← Port secondaire (interface)

        /**
         * Le service reçoit le port par injection.
         * Il ne sait pas s'il parle à InMemory, JPA, MongoDB, etc.
         */
        public OrderApplicationService(OrderRepository orderRepository) {
            this.orderRepository = orderRepository;
        }

        public Order createAndPlaceOrder(String orderId, String customerId,
                                         String productId, String productName,
                                         int quantity, BigDecimal unitPrice) {
            System.out.println("  [SERVICE] Création de la commande " + orderId);

            Order order = new Order(orderId, customerId);
            order.addItem(productId, productName, quantity, unitPrice);
            order.place();

            // Appel au port secondaire - le service ne sait pas COMMENT c'est persisté
            System.out.println("  [SERVICE] Sauvegarde via le repository...");
            orderRepository.save(order);

            return order;
        }

        public Optional<Order> findOrder(String orderId) {
            System.out.println("  [SERVICE] Recherche de la commande " + orderId);
            return orderRepository.findById(orderId);
        }

        public void confirmOrder(String orderId) {
            System.out.println("  [SERVICE] Confirmation de la commande " + orderId);

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Commande introuvable : " + orderId));

            order.confirm();
            orderRepository.save(order);
        }
    }

    // =========================================================================
    // DÉMONSTRATION
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=== Demo 07 : Port Secondaire et Adaptateurs Repository ===\n");

        System.out.println("Architecture :");
        System.out.println("  [OrderApplicationService] → [OrderRepository (port/interface)]");
        System.out.println("                                         ↑");
        System.out.println("              ┌──────────────────────────┼──────────────────┐");
        System.out.println("  [InMemoryOrderRepository]    [JpaOrderRepository]   [MongoRepository...]");
        System.out.println("     (tests)                     (production)           (futur)");
        System.out.println();

        // =================================================================
        // SCÉNARIO 1 : Avec l'adaptateur InMemory (tests)
        // =================================================================
        System.out.println("========================================");
        System.out.println("SCÉNARIO 1 : Adaptateur InMemory (tests)");
        System.out.println("========================================\n");

        OrderRepository inMemoryRepo = new InMemoryOrderRepository();
        OrderApplicationService serviceInMemory = new OrderApplicationService(inMemoryRepo);

        Order order1 = serviceInMemory.createAndPlaceOrder(
                "CMD-001", "CLIENT-42",
                "PROD-A", "Clavier mécanique", 1, new BigDecimal("89.99"));
        System.out.println("  → " + order1 + "\n");

        serviceInMemory.confirmOrder("CMD-001");
        Optional<Order> found = serviceInMemory.findOrder("CMD-001");
        found.ifPresent(o -> System.out.println("  → Après confirmation : " + o));
        System.out.println("  Repository : " + inMemoryRepo);

        // =================================================================
        // SCÉNARIO 2 : Même service, adaptateur JPA (production)
        // =================================================================
        System.out.println("\n========================================");
        System.out.println("SCÉNARIO 2 : Adaptateur JPA (production)");
        System.out.println("========================================\n");

        OrderRepository jpaRepo = new JpaOrderRepository();
        // Même service, même code, adaptateur différent !
        OrderApplicationService serviceJpa = new OrderApplicationService(jpaRepo);

        Order order2 = serviceJpa.createAndPlaceOrder(
                "CMD-002", "CLIENT-77",
                "PROD-B", "Écran 4K", 2, new BigDecimal("499.99"));
        System.out.println("  → " + order2 + "\n");

        serviceJpa.confirmOrder("CMD-002");
        Optional<Order> foundJpa = serviceJpa.findOrder("CMD-002");
        foundJpa.ifPresent(o -> System.out.println("  → Après confirmation : " + o));
        System.out.println("  Repository : " + jpaRepo);

        // =================================================================
        // SCÉNARIO 3 : Démonstration du mapper
        // =================================================================
        System.out.println("\n========================================");
        System.out.println("SCÉNARIO 3 : Mapping domaine ↔ JPA");
        System.out.println("========================================\n");

        Order domainOrder = new Order("CMD-MAP-001", "CLIENT-99");
        domainOrder.addItem("PROD-X", "Widget", 3, new BigDecimal("19.99"));
        domainOrder.addItem("PROD-Y", "Gadget", 1, new BigDecimal("49.99"));
        domainOrder.place();

        System.out.println("Entité domaine originale : " + domainOrder);
        System.out.println("  Articles : " + domainOrder.getItems().size());

        // Domaine → JPA
        OrderJpaEntity jpaEntity = OrderMapper.toJpaEntity(domainOrder);
        System.out.println("\nAprès mapping domaine → JPA : " + jpaEntity);
        System.out.println("  Status (String) : " + jpaEntity.getStatus());
        System.out.println("  Items JPA : " + jpaEntity.getItems().size());

        // JPA → Domaine (reconstitution)
        Order reconstituted = OrderMapper.toDomain(jpaEntity);
        System.out.println("\nAprès mapping JPA → domaine : " + reconstituted);
        System.out.println("  Status (Enum) : " + reconstituted.getStatus());
        System.out.println("  Articles : " + reconstituted.getItems().size());

        System.out.println("\n=== Points clés ===");
        System.out.println("1. Le port (OrderRepository) est défini dans le domaine");
        System.out.println("2. Les adaptateurs implémentent le port dans l'infrastructure");
        System.out.println("3. Le service applicatif ne connaît que le port (interface)");
        System.out.println("4. On peut changer d'adaptateur sans toucher au domaine");
        System.out.println("5. Le mapper isole les modèles domaine et persistance");
        System.out.println("6. L'adaptateur InMemory est idéal pour les tests rapides");

        System.out.println("\n=== Fin de la démo ===");
    }
}
