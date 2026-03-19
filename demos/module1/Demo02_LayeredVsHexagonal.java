package com.utopios.hexagonal.demos.module1;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DEMO 02 - Architecture en Couches vs Architecture Hexagonale
 * =============================================================
 *
 * Comparaison cote a cote des deux approches pour le meme cas d'usage :
 *   - Gerer des commandes (Order) avec calcul de total et validation
 *
 * PARTIE 1 : Approche en couches classique (Layered)
 *   -> Le service depend d'un repository JPA (infrastructure)
 *   -> La fleche de dependance pointe VERS le bas (service -> repo -> BDD)
 *
 * PARTIE 2 : Approche hexagonale
 *   -> Le service depend d'un PORT (interface du domaine)
 *   -> L'adaptateur implemente le port
 *   -> La fleche de dependance est INVERSEE (adapter -> port <- service)
 *
 * A executer : javac Demo02_LayeredVsHexagonal.java && java -cp . com.utopios.hexagonal.demos.module1.Demo02_LayeredVsHexagonal
 */
public class Demo02_LayeredVsHexagonal {

    // ====================================================================
    //  MODELE COMMUN - Utilise par les deux approches
    //  On utilise un record Java 17+ pour un objet valeur immutable
    // ====================================================================

    /** Ligne de commande : produit + quantite + prix unitaire */
    record OrderLine(String product, int quantity, double unitPrice) {
        /** Calcul du sous-total - c'est de la logique METIER */
        double subtotal() {
            return quantity * unitPrice;
        }
    }

    // ====================================================================
    //
    //  PARTIE 1 : APPROCHE EN COUCHES (LAYERED ARCHITECTURE)
    //
    //  Probleme : le service metier connait et depend de l'infrastructure.
    //
    //  Controller -> Service -> Repository (JPA) -> Base de donnees
    //       ^            |            |
    //       |            v            v
    //       |      Depend de    Depend de JPA
    //       |      JPA Repository
    //
    //  La direction des dependances suit la direction des appels.
    //  Le domaine metier est "en sandwich" entre la presentation et
    //  l'infrastructure.
    //
    // ====================================================================

    static class Layered {

        /**
         * Entite "Order" - En couches classique, cette classe
         * serait annotee @Entity, @Table, etc.
         * Le domaine est pollue par les annotations JPA.
         */
        static class Order {
            private Long id;
            private String customerName;
            private final List<OrderLine> lines = new ArrayList<>();
            private String status; // CREATED, VALIDATED, SHIPPED

            Order(String customerName) {
                this.customerName = customerName;
                this.status = "CREATED";
            }

            void addLine(OrderLine line) { lines.add(line); }
            double total() { return lines.stream().mapToDouble(OrderLine::subtotal).sum(); }

            Long getId() { return id; }
            void setId(Long id) { this.id = id; }
            String getCustomerName() { return customerName; }
            String getStatus() { return status; }
            void setStatus(String status) { this.status = status; }
            List<OrderLine> getLines() { return lines; }

            @Override
            public String toString() {
                return "Order{id=%d, customer='%s', status='%s', total=%.2f, lignes=%d}"
                        .formatted(id, customerName, status, total(), lines.size());
            }
        }

        /**
         * REPOSITORY JPA - En couches classique, ceci serait un
         * JpaRepository<Order, Long> de Spring Data.
         *
         * Le service metier depend DIRECTEMENT de cette interface
         * qui vit dans la couche infrastructure.
         */
        // Simulation de : interface OrderRepository extends JpaRepository<Order, Long>
        static class JpaOrderRepository {
            private final Map<Long, Order> store = new HashMap<>();
            private long seq = 0;

            Order save(Order order) {
                seq++;
                order.setId(seq);
                store.put(seq, order);
                System.out.println("    [JPA] save() -> INSERT avec id=" + seq);
                return order;
            }

            Optional<Order> findById(Long id) {
                System.out.println("    [JPA] findById(" + id + ")");
                return Optional.ofNullable(store.get(id));
            }
        }

        /**
         * SERVICE METIER - Couches classique
         *
         * PROBLEME : ce service depend DIRECTEMENT de JpaOrderRepository.
         * - On ne peut pas le tester sans instancier le repository JPA
         * - On ne peut pas changer de persistence sans modifier ce code
         * - La logique metier est couplee a l'infrastructure
         */
        static class OrderService {
            // DEPENDANCE DIRECTE vers l'infrastructure !
            private final JpaOrderRepository repository;

            OrderService(JpaOrderRepository repository) {
                this.repository = repository;
            }

            Order createOrder(String customer, List<OrderLine> lines) {
                var order = new Order(customer);
                lines.forEach(order::addLine);

                // Regle metier : commande minimum 10 EUR
                if (order.total() < 10.0) {
                    throw new IllegalArgumentException(
                            "Commande minimum : 10 EUR (actuel : %.2f EUR)".formatted(order.total()));
                }

                return repository.save(order);
            }

            Order validateOrder(Long orderId) {
                var order = repository.findById(orderId)
                        .orElseThrow(() -> new IllegalArgumentException("Commande introuvable"));
                order.setStatus("VALIDATED");
                return repository.save(order);
            }
        }

        /** Executer la demo de l'approche en couches */
        static void run() {
            System.out.println("  APPROCHE EN COUCHES (Layered)");
            System.out.println("  " + "-".repeat(40));
            System.out.println("  Direction des dependances :");
            System.out.println("    OrderService --> JpaOrderRepository --> BDD");
            System.out.println("    (le service connait JPA)");
            System.out.println();

            // Le service est couple a JPA des sa creation
            var repo = new JpaOrderRepository();
            var service = new OrderService(repo);

            var order = service.createOrder("Alice", List.of(
                    new OrderLine("Clean Code", 1, 35.00),
                    new OrderLine("Refactoring", 2, 29.99)
            ));
            System.out.println("    Commande creee : " + order);

            var validated = service.validateOrder(order.getId());
            System.out.println("    Commande validee : " + validated);
            System.out.println();
        }
    }

    // ====================================================================
    //
    //  PARTIE 2 : APPROCHE HEXAGONALE (PORTS & ADAPTERS)
    //
    //  Le service metier ne connait QUE le PORT (interface du domaine).
    //  L'adaptateur (JPA, InMemory, etc.) implemente le port.
    //
    //  Adapter --> Port (interface) <-- Service (domaine)
    //     |                               |
    //     v                               v
    //  Infrastructure              Logique metier pure
    //
    //  La dependance est INVERSEE : c'est l'infrastructure qui depend
    //  du domaine, et non l'inverse.
    //
    // ====================================================================

    static class Hexagonal {

        /**
         * Entite du DOMAINE - Aucune annotation infrastructure.
         * C'est un objet metier pur.
         */
        static class Order {
            private Long id;
            private final String customerName;
            private final List<OrderLine> lines = new ArrayList<>();
            private String status;

            Order(String customerName) {
                this.customerName = customerName;
                this.status = "CREATED";
            }

            void addLine(OrderLine line) { lines.add(line); }
            double total() { return lines.stream().mapToDouble(OrderLine::subtotal).sum(); }

            Long getId() { return id; }
            void setId(Long id) { this.id = id; }
            String getCustomerName() { return customerName; }
            String getStatus() { return status; }
            void setStatus(String status) { this.status = status; }

            @Override
            public String toString() {
                return "Order{id=%d, customer='%s', status='%s', total=%.2f}"
                        .formatted(id, customerName, status, total());
            }
        }

        /**
         * PORT SORTANT (Driven Port) - C'est une interface du DOMAINE.
         *
         * Elle definit ce dont le domaine a BESOIN, sans savoir
         * comment c'est implemente. Le domaine dicte le contrat.
         *
         * C'est le coeur de l'inversion de dependance !
         */
        interface OrderRepository {
            Order save(Order order);
            Optional<Order> findById(Long id);
        }

        /**
         * SERVICE METIER (Use Case / Application Service)
         *
         * DIFFERENCE CLE : ce service depend du PORT (interface),
         * pas d'une implementation concrete.
         *
         * Il ne sait pas si les donnees vont en base SQL, en memoire,
         * dans un fichier, ou vers une API REST.
         */
        static class OrderService {
            // DEPENDANCE vers le PORT (abstraction du domaine)
            private final OrderRepository repository;

            OrderService(OrderRepository repository) {
                this.repository = repository;
            }

            Order createOrder(String customer, List<OrderLine> lines) {
                var order = new Order(customer);
                lines.forEach(order::addLine);

                // Regle metier pure - aucune dependance infrastructure
                if (order.total() < 10.0) {
                    throw new IllegalArgumentException(
                            "Commande minimum : 10 EUR (actuel : %.2f EUR)".formatted(order.total()));
                }

                return repository.save(order);
            }

            Order validateOrder(Long orderId) {
                var order = repository.findById(orderId)
                        .orElseThrow(() -> new IllegalArgumentException("Commande introuvable"));
                order.setStatus("VALIDATED");
                return repository.save(order);
            }
        }

        // ---------------------------------------------------------------
        //  ADAPTATEURS - Ils implementent le port du domaine.
        //  On peut en creer autant qu'on veut sans toucher au domaine.
        // ---------------------------------------------------------------

        /** Adaptateur JPA - Pour la production */
        static class JpaOrderAdapter implements OrderRepository {
            private final Map<Long, Order> store = new HashMap<>();
            private long seq = 0;

            @Override
            public Order save(Order order) {
                seq++;
                order.setId(seq);
                store.put(seq, order);
                System.out.println("    [JPA Adapter] INSERT avec id=" + seq);
                return order;
            }

            @Override
            public Optional<Order> findById(Long id) {
                System.out.println("    [JPA Adapter] SELECT id=" + id);
                return Optional.ofNullable(store.get(id));
            }
        }

        /** Adaptateur InMemory - Pour les tests unitaires */
        static class InMemoryOrderAdapter implements OrderRepository {
            private final Map<Long, Order> store = new HashMap<>();
            private long seq = 0;

            @Override
            public Order save(Order order) {
                seq++;
                order.setId(seq);
                store.put(seq, order);
                System.out.println("    [InMemory Adapter] stockage en memoire, id=" + seq);
                return order;
            }

            @Override
            public Optional<Order> findById(Long id) {
                System.out.println("    [InMemory Adapter] lecture memoire, id=" + id);
                return Optional.ofNullable(store.get(id));
            }
        }

        /** Executer la demo de l'approche hexagonale */
        static void run() {
            System.out.println("  APPROCHE HEXAGONALE (Ports & Adapters)");
            System.out.println("  " + "-".repeat(40));
            System.out.println("  Direction des dependances :");
            System.out.println("    JpaAdapter --> OrderRepository (port) <-- OrderService");
            System.out.println("    (le service ne connait PAS JPA)");
            System.out.println();

            // --- Avec l'adaptateur JPA (production) ---
            System.out.println("  >> Avec l'adaptateur JPA :");
            OrderRepository jpaAdapter = new JpaOrderAdapter();
            var serviceWithJpa = new OrderService(jpaAdapter);

            var order1 = serviceWithJpa.createOrder("Bob", List.of(
                    new OrderLine("Clean Code", 1, 35.00),
                    new OrderLine("Refactoring", 2, 29.99)
            ));
            System.out.println("    Commande creee : " + order1);
            System.out.println();

            // --- Avec l'adaptateur InMemory (test) ---
            // MEME SERVICE, MEME LOGIQUE METIER, adaptateur different !
            System.out.println("  >> Avec l'adaptateur InMemory (pour les tests) :");
            OrderRepository inMemoryAdapter = new InMemoryOrderAdapter();
            var serviceWithMemory = new OrderService(inMemoryAdapter);

            var order2 = serviceWithMemory.createOrder("Charlie", List.of(
                    new OrderLine("DDD", 1, 50.00)
            ));
            System.out.println("    Commande creee : " + order2);

            var validated = serviceWithMemory.validateOrder(order2.getId());
            System.out.println("    Commande validee : " + validated);
            System.out.println();
        }
    }

    // ====================================================================
    //  POINT D'ENTREE
    // ====================================================================
    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("DEMO 02 : Architecture en Couches vs Architecture Hexagonale");
        System.out.println("=".repeat(70));
        System.out.println();

        // --- Partie 1 : Approche en couches ---
        System.out.println(">>> PARTIE 1 : Approche en couches");
        System.out.println("-".repeat(50));
        Layered.run();

        // --- Partie 2 : Approche hexagonale ---
        System.out.println(">>> PARTIE 2 : Approche hexagonale");
        System.out.println("-".repeat(50));
        Hexagonal.run();

        // --- Comparaison finale ---
        System.out.println("=".repeat(70));
        System.out.println("COMPARAISON");
        System.out.println("-".repeat(70));
        System.out.println();
        System.out.println("  Critere                 | Couches         | Hexagonale");
        System.out.println("  " + "-".repeat(62));
        System.out.println("  Dependance du service   | -> Infrastructure | -> Port (abstraction)");
        System.out.println("  Testabilite             | Difficile        | Facile (InMemory)");
        System.out.println("  Changer de BDD          | Modifier service | Nouveau adapter");
        System.out.println("  Qui dicte le contrat ?  | Infrastructure   | Domaine metier");
        System.out.println("  Principe DIP            | Viole            | Respecte");
        System.out.println();
        System.out.println("  --> Le domaine metier est PROTEGE des changements d'infrastructure");
        System.out.println("=".repeat(70));
    }
}
