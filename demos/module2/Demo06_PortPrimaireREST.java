package com.utopios.hexagonal.demos.module2;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Demo 06 - Port Primaire et Adaptateur REST
 *
 * Illustre le pattern Ports & Adapters côté "driving" (primaire) :
 * - Le PORT PRIMAIRE est l'interface que le domaine expose au monde extérieur
 * - L'ADAPTATEUR PRIMAIRE (ici REST) traduit les requêtes HTTP en appels au port
 * - Le SERVICE APPLICATIF implémente le port et orchestre la logique métier
 *
 * Direction du flux :
 *   [Client HTTP] → [RestController (adaptateur)] → [OrderService (port)] ← [OrderServiceImpl (service)]
 *
 * Note : Pseudo-code REST sans dépendance Spring. Les annotations sont simulées.
 */
public class Demo06_PortPrimaireREST {

    // =========================================================================
    // MODÈLE DU DOMAINE (simplifié pour la démo)
    // =========================================================================

    /**
     * Entité Order du domaine.
     */
    public static class Order {
        public enum Status { DRAFT, PLACED, CONFIRMED, CANCELLED }

        private final String orderId;
        private final String customerId;
        private final List<String> items;
        private BigDecimal totalAmount;
        private Status status;
        private final Instant createdAt;

        public Order(String orderId, String customerId, List<String> items, BigDecimal totalAmount) {
            this.orderId = Objects.requireNonNull(orderId);
            this.customerId = Objects.requireNonNull(customerId);
            this.items = new ArrayList<>(items);
            this.totalAmount = totalAmount;
            this.status = Status.PLACED;
            this.createdAt = Instant.now();
        }

        public void confirm() {
            if (status != Status.PLACED) {
                throw new IllegalStateException("Seule une commande passée peut être confirmée");
            }
            this.status = Status.CONFIRMED;
        }

        public void cancel(String reason) {
            if (status == Status.CANCELLED) {
                throw new IllegalStateException("La commande est déjà annulée");
            }
            this.status = Status.CANCELLED;
        }

        public String getOrderId() { return orderId; }
        public String getCustomerId() { return customerId; }
        public List<String> getItems() { return Collections.unmodifiableList(items); }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public Status getStatus() { return status; }
        public Instant getCreatedAt() { return createdAt; }

        @Override
        public String toString() {
            return String.format("Order[id=%s, client=%s, articles=%d, total=%s, statut=%s]",
                    orderId, customerId, items.size(), totalAmount, status);
        }
    }

    // =========================================================================
    // PORT PRIMAIRE - Interface exposée par le domaine
    // =========================================================================

    /**
     * Port primaire : contrat que le domaine offre au monde extérieur.
     *
     * C'est une INTERFACE définie dans la couche domaine/application.
     * Elle exprime les cas d'utilisation en termes métier.
     * Aucune notion technique (HTTP, JSON, etc.) n'apparaît ici.
     */
    public interface OrderService {

        /**
         * Passe une nouvelle commande.
         * @param command les données nécessaires pour créer la commande
         * @return la commande créée
         */
        Order placeOrder(PlaceOrderCommand command);

        /**
         * Annule une commande existante.
         * @param orderId identifiant de la commande
         * @param reason raison de l'annulation
         */
        void cancelOrder(String orderId, String reason);

        /**
         * Récupère une commande par son identifiant.
         * @param orderId identifiant de la commande
         * @return la commande ou vide si introuvable
         */
        Optional<Order> getOrder(String orderId);

        /**
         * Liste toutes les commandes d'un client.
         */
        List<Order> getOrdersByCustomer(String customerId);
    }

    /**
     * Objet de commande (Command Pattern) pour le cas d'utilisation "Passer une commande".
     * Regroupe les données nécessaires sans logique.
     */
    public record PlaceOrderCommand(
            String customerId,
            List<String> items,
            BigDecimal totalAmount
    ) {
        public PlaceOrderCommand {
            Objects.requireNonNull(customerId, "L'identifiant client est requis");
            Objects.requireNonNull(items, "La liste des articles est requise");
            if (items.isEmpty()) {
                throw new IllegalArgumentException("Au moins un article est requis");
            }
            if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Le montant total doit être positif");
            }
        }
    }

    // =========================================================================
    // SERVICE APPLICATIF - Implémente le port primaire
    // =========================================================================

    /**
     * Service applicatif qui implémente le port primaire.
     *
     * Responsabilités :
     * - Orchestrer les appels au domaine
     * - Gérer les transactions (simulé ici)
     * - Ne contient PAS de logique métier (celle-ci est dans les entités)
     */
    public static class OrderServiceImpl implements OrderService {

        // Simulation d'un repository (port secondaire en pratique)
        private final Map<String, Order> orderStore = new HashMap<>();
        private int orderSequence = 0;

        @Override
        public Order placeOrder(PlaceOrderCommand command) {
            System.out.println("  [SERVICE] Traitement de la commande...");

            // Générer un identifiant
            String orderId = "CMD-" + String.format("%04d", ++orderSequence);

            // Créer l'entité domaine
            Order order = new Order(orderId, command.customerId(),
                    command.items(), command.totalAmount());

            // Persister (via port secondaire en vrai)
            orderStore.put(orderId, order);

            System.out.println("  [SERVICE] Commande créée : " + orderId);
            return order;
        }

        @Override
        public void cancelOrder(String orderId, String reason) {
            System.out.println("  [SERVICE] Annulation de la commande " + orderId);

            Order order = orderStore.get(orderId);
            if (order == null) {
                throw new IllegalArgumentException("Commande introuvable : " + orderId);
            }

            // La logique métier est dans l'entité, pas dans le service
            order.cancel(reason);

            System.out.println("  [SERVICE] Commande annulée : " + orderId);
        }

        @Override
        public Optional<Order> getOrder(String orderId) {
            return Optional.ofNullable(orderStore.get(orderId));
        }

        @Override
        public List<Order> getOrdersByCustomer(String customerId) {
            return orderStore.values().stream()
                    .filter(o -> o.getCustomerId().equals(customerId))
                    .toList();
        }
    }

    // =========================================================================
    // DTOs - Objets de transfert pour l'API REST
    // =========================================================================

    /**
     * DTO de requête pour la création de commande.
     * Représente le corps JSON de la requête HTTP.
     * Distinct du Command du domaine : c'est un objet technique.
     */
    public record CreateOrderRequest(
            String customerId,
            List<String> items,
            BigDecimal totalAmount
    ) {}

    /**
     * DTO de réponse pour l'API REST.
     * Ne contient que les informations nécessaires au client HTTP.
     */
    public record OrderResponse(
            String orderId,
            String customerId,
            List<String> items,
            BigDecimal totalAmount,
            String status,
            String createdAt
    ) {
        /**
         * Factory method pour convertir une entité domaine en DTO.
         * Cette conversion est la responsabilité de l'adaptateur, pas du domaine.
         */
        public static OrderResponse fromDomain(Order order) {
            return new OrderResponse(
                    order.getOrderId(),
                    order.getCustomerId(),
                    order.getItems(),
                    order.getTotalAmount(),
                    order.getStatus().name(),
                    order.getCreatedAt().toString()
            );
        }
    }

    /**
     * DTO pour l'annulation.
     */
    public record CancelOrderRequest(String reason) {}

    // =========================================================================
    // SIMULATION HTTP - Modèles simplifiés de requête/réponse
    // =========================================================================

    /**
     * Simulation minimaliste d'une réponse HTTP.
     */
    public record HttpResponse<T>(int statusCode, T body, String message) {

        public static <T> HttpResponse<T> ok(T body) {
            return new HttpResponse<>(200, body, "OK");
        }

        public static <T> HttpResponse<T> created(T body) {
            return new HttpResponse<>(201, body, "Created");
        }

        public static <T> HttpResponse<T> notFound(String message) {
            return new HttpResponse<>(404, null, message);
        }

        public static <T> HttpResponse<T> badRequest(String message) {
            return new HttpResponse<>(400, null, message);
        }

        @Override
        public String toString() {
            return String.format("HTTP %d %s%s", statusCode, message,
                    body != null ? " | Body: " + body : "");
        }
    }

    // =========================================================================
    // ADAPTATEUR PRIMAIRE - Contrôleur REST
    // =========================================================================

    /**
     * Adaptateur primaire REST : traduit les requêtes HTTP en appels au port primaire.
     *
     * Responsabilités de l'adaptateur :
     * 1. Désérialiser la requête HTTP (JSON → DTO)
     * 2. Convertir le DTO en objets du domaine (DTO → Command)
     * 3. Appeler le port primaire (OrderService)
     * 4. Convertir la réponse domaine en DTO (Entity → ResponseDTO)
     * 5. Sérialiser la réponse HTTP (DTO → JSON)
     *
     * L'adaptateur NE CONTIENT PAS de logique métier.
     *
     * Pseudo-annotations :
     * // @RestController
     * // @RequestMapping("/api/orders")
     */
    public static class OrderRestController {

        // L'adaptateur dépend du PORT (interface), pas de l'implémentation
        private final OrderService orderService;

        /**
         * Injection du port par le constructeur.
         * Le contrôleur ne connaît pas l'implémentation concrète.
         */
        public OrderRestController(OrderService orderService) {
            this.orderService = orderService;
        }

        /**
         * POST /api/orders
         * Crée une nouvelle commande.
         */
        // @PostMapping
        public HttpResponse<OrderResponse> createOrder(CreateOrderRequest request) {
            System.out.println("  [REST] POST /api/orders");
            System.out.println("  [REST] Body reçu : " + request);

            try {
                // Étape 1 : Convertir le DTO en Command du domaine
                PlaceOrderCommand command = new PlaceOrderCommand(
                        request.customerId(),
                        request.items(),
                        request.totalAmount()
                );

                // Étape 2 : Appeler le port primaire
                Order order = orderService.placeOrder(command);

                // Étape 3 : Convertir la réponse domaine en DTO
                OrderResponse response = OrderResponse.fromDomain(order);

                return HttpResponse.created(response);

            } catch (IllegalArgumentException e) {
                return HttpResponse.badRequest(e.getMessage());
            }
        }

        /**
         * GET /api/orders/{orderId}
         * Récupère une commande par son identifiant.
         */
        // @GetMapping("/{orderId}")
        public HttpResponse<OrderResponse> getOrder(String orderId) {
            System.out.println("  [REST] GET /api/orders/" + orderId);

            return orderService.getOrder(orderId)
                    .map(order -> HttpResponse.ok(OrderResponse.fromDomain(order)))
                    .orElse(HttpResponse.notFound("Commande introuvable : " + orderId));
        }

        /**
         * DELETE /api/orders/{orderId}
         * Annule une commande.
         */
        // @DeleteMapping("/{orderId}")
        public HttpResponse<Void> cancelOrder(String orderId, CancelOrderRequest request) {
            System.out.println("  [REST] DELETE /api/orders/" + orderId);

            try {
                orderService.cancelOrder(orderId, request.reason());
                return HttpResponse.ok(null);
            } catch (IllegalArgumentException e) {
                return HttpResponse.notFound(e.getMessage());
            } catch (IllegalStateException e) {
                return HttpResponse.badRequest(e.getMessage());
            }
        }

        /**
         * GET /api/orders?customerId={customerId}
         * Liste les commandes d'un client.
         */
        // @GetMapping(params = "customerId")
        public HttpResponse<List<OrderResponse>> getOrdersByCustomer(String customerId) {
            System.out.println("  [REST] GET /api/orders?customerId=" + customerId);

            List<OrderResponse> responses = orderService.getOrdersByCustomer(customerId)
                    .stream()
                    .map(OrderResponse::fromDomain)
                    .toList();

            return HttpResponse.ok(responses);
        }
    }

    // =========================================================================
    // DÉMONSTRATION
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=== Demo 06 : Port Primaire et Adaptateur REST ===\n");

        // --- Assemblage (normalement fait par le framework DI) ---
        // Le contrôleur reçoit le port, pas l'implémentation concrète
        OrderService service = new OrderServiceImpl();        // implémente le port
        OrderRestController controller = new OrderRestController(service); // utilise le port

        System.out.println("Architecture :");
        System.out.println("  [Client HTTP] → [OrderRestController] → [OrderService (port)]");
        System.out.println("                                              ↑");
        System.out.println("                                     [OrderServiceImpl (service)]");
        System.out.println();

        // --- Scénario 1 : Création de commande ---
        System.out.println("--- Scénario 1 : Création de commande ---");
        CreateOrderRequest createRequest = new CreateOrderRequest(
                "CLIENT-42",
                List.of("Clavier mécanique", "Souris ergonomique"),
                new BigDecimal("89.98")
        );

        HttpResponse<OrderResponse> createResponse = controller.createOrder(createRequest);
        System.out.println("  Réponse : " + createResponse);
        System.out.println();

        // --- Scénario 2 : Consultation d'une commande ---
        System.out.println("--- Scénario 2 : Consultation ---");
        HttpResponse<OrderResponse> getResponse = controller.getOrder("CMD-0001");
        System.out.println("  Réponse : " + getResponse);
        System.out.println();

        // --- Scénario 3 : Commande introuvable ---
        System.out.println("--- Scénario 3 : Commande introuvable ---");
        HttpResponse<OrderResponse> notFound = controller.getOrder("CMD-9999");
        System.out.println("  Réponse : " + notFound);
        System.out.println();

        // --- Scénario 4 : Annulation ---
        System.out.println("--- Scénario 4 : Annulation ---");
        CancelOrderRequest cancelRequest = new CancelOrderRequest("Client a changé d'avis");
        HttpResponse<Void> cancelResponse = controller.cancelOrder("CMD-0001", cancelRequest);
        System.out.println("  Réponse : " + cancelResponse);
        System.out.println();

        // Vérification du statut après annulation
        System.out.println("--- Vérification post-annulation ---");
        HttpResponse<OrderResponse> afterCancel = controller.getOrder("CMD-0001");
        System.out.println("  Réponse : " + afterCancel);
        System.out.println();

        // --- Scénario 5 : Requête invalide ---
        System.out.println("--- Scénario 5 : Requête invalide (liste d'articles vide) ---");
        CreateOrderRequest invalidRequest = new CreateOrderRequest(
                "CLIENT-42", List.of(), new BigDecimal("0")
        );
        HttpResponse<OrderResponse> badRequest = controller.createOrder(invalidRequest);
        System.out.println("  Réponse : " + badRequest);
        System.out.println();

        // --- Scénario 6 : Plusieurs commandes d'un même client ---
        System.out.println("--- Scénario 6 : Commandes par client ---");
        controller.createOrder(new CreateOrderRequest(
                "CLIENT-42", List.of("Écran 27 pouces"), new BigDecimal("349.99")));
        controller.createOrder(new CreateOrderRequest(
                "CLIENT-42", List.of("Hub USB-C"), new BigDecimal("29.99")));

        HttpResponse<List<OrderResponse>> customerOrders =
                controller.getOrdersByCustomer("CLIENT-42");
        System.out.println("  Réponse : " + customerOrders.statusCode()
                + " | Nombre de commandes : "
                + (customerOrders.body() != null ? customerOrders.body().size() : 0));

        System.out.println("\n=== Points clés ===");
        System.out.println("1. Le contrôleur (adaptateur) traduit HTTP ↔ domaine");
        System.out.println("2. Le service implémente le port sans connaître l'adaptateur");
        System.out.println("3. Le port (interface) est défini dans le domaine");
        System.out.println("4. Les DTOs vivent dans l'adaptateur, pas dans le domaine");
        System.out.println("5. La logique métier reste dans les entités, pas dans le contrôleur");

        System.out.println("\n=== Fin de la démo ===");
    }
}
