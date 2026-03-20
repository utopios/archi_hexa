package com.utopios.hexagonal.demos.module4;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Demo 13 - CQRS (Command Query Responsibility Segregation)
 *
 * CQRS sépare les opérations d'écriture (Commands) et de lecture (Queries)
 * en deux modèles distincts :
 *
 *   - Write Model (normalisé) : optimisé pour la cohérence et les règles métier
 *   - Read Model (dénormalisé) : optimisé pour les performances de lecture
 *
 * En architecture hexagonale :
 *   - CommandHandler = port d'entrée côté écriture
 *   - QueryHandler = port d'entrée côté lecture
 *   - CommandBus / QueryBus = infrastructure de dispatch
 */
public class Demo13_CQRS {

    // =========================================================================
    // COMMANDES (intentions de modification)
    // =========================================================================

    /** Interface marqueur pour toutes les commandes */
    sealed interface Command permits CreateProductCommand, UpdatePriceCommand, DeactivateProductCommand {}

    /** Commande : créer un produit */
    record CreateProductCommand(
        String productId,
        String nom,
        String categorie,
        double prix,
        int stock
    ) implements Command {}

    /** Commande : mettre à jour le prix */
    record UpdatePriceCommand(
        String productId,
        double nouveauPrix,
        String motif
    ) implements Command {}

    /** Commande : désactiver un produit */
    record DeactivateProductCommand(
        String productId,
        String raison
    ) implements Command {}

    // =========================================================================
    // QUERIES (demandes de lecture)
    // =========================================================================

    /** Interface marqueur pour toutes les queries */
    sealed interface Query<R> permits GetProductQuery, SearchProductsQuery, GetCatalogueStatsQuery {}

    /** Query : obtenir un produit par ID */
    record GetProductQuery(String productId) implements Query<ProductReadModel> {}

    /** Query : rechercher des produits */
    record SearchProductsQuery(
        String categorie,
        Double prixMin,
        Double prixMax,
        Boolean actifUniquement
    ) implements Query<List<ProductReadModel>> {}

    /** Query : statistiques du catalogue */
    record GetCatalogueStatsQuery() implements Query<CatalogueStats> {}

    // =========================================================================
    // WRITE MODEL (normalisé, orienté cohérence)
    // =========================================================================

    /**
     * Le Write Model est l'entité du domaine.
     * Il protège les invariants et applique les règles métier.
     * Stocké de manière normalisée (ex: tables SQL relationnelles).
     */
    static final class Product {
        private final String id;
        private String nom;
        private String categorie;
        private double prix;
        private int stock;
        private boolean actif;
        private final List<PriceChange> historiquePrix;
        private final Instant createdAt;

        Product(String id, String nom, String categorie, double prix, int stock) {
            if (prix <= 0) throw new IllegalArgumentException("Le prix doit être positif");
            if (stock < 0) throw new IllegalArgumentException("Le stock ne peut pas être négatif");
            this.id = id;
            this.nom = nom;
            this.categorie = categorie;
            this.prix = prix;
            this.stock = stock;
            this.actif = true;
            this.historiquePrix = new ArrayList<>();
            this.historiquePrix.add(new PriceChange(prix, "Prix initial", Instant.now()));
            this.createdAt = Instant.now();
        }

        /** Règle métier : changement de prix avec historique */
        void changerPrix(double nouveauPrix, String motif) {
            if (!actif) throw new IllegalStateException("Produit inactif");
            if (nouveauPrix <= 0) throw new IllegalArgumentException("Prix invalide");
            double variation = ((nouveauPrix - prix) / prix) * 100;
            if (Math.abs(variation) > 50) {
                throw new IllegalArgumentException(
                    String.format("Variation de prix trop importante : %.1f%% (max 50%%)", variation)
                );
            }
            this.historiquePrix.add(new PriceChange(nouveauPrix, motif, Instant.now()));
            this.prix = nouveauPrix;
        }

        void desactiver(String raison) {
            if (!actif) throw new IllegalStateException("Déjà inactif");
            this.actif = false;
        }

        // Accesseurs
        String getId() { return id; }
        String getNom() { return nom; }
        String getCategorie() { return categorie; }
        double getPrix() { return prix; }
        int getStock() { return stock; }
        boolean isActif() { return actif; }
        List<PriceChange> getHistoriquePrix() { return Collections.unmodifiableList(historiquePrix); }
        Instant getCreatedAt() { return createdAt; }
    }

    record PriceChange(double prix, String motif, Instant date) {}

    // =========================================================================
    // READ MODEL (dénormalisé, optimisé pour la lecture)
    // =========================================================================

    /**
     * Le Read Model est une projection plate, dénormalisée.
     * Il contient exactement ce dont les vues/API ont besoin.
     * Pas de jointures, pas de calculs → lecture ultra-rapide.
     */
    record ProductReadModel(
        String productId,
        String nom,
        String categorie,
        double prix,
        int stock,
        boolean actif,
        boolean enStock,         // dénormalisé : stock > 0
        String rangePrix,        // dénormalisé : "Budget", "Standard", "Premium"
        int nombreChangementsPrix,
        Instant derniereModification
    ) {}

    record CatalogueStats(
        int totalProduits,
        int produitsActifs,
        int produitsEnStock,
        double prixMoyen,
        Map<String, Integer> parCategorie
    ) {}

    // =========================================================================
    // WRITE STORE (repository pour le modèle d'écriture)
    // =========================================================================

    /** Stockage du Write Model (normalisé) */
    static class WriteStore {
        private final Map<String, Product> products = new ConcurrentHashMap<>();

        void save(Product product) {
            products.put(product.getId(), product);
        }

        Optional<Product> findById(String id) {
            return Optional.ofNullable(products.get(id));
        }
    }

    // =========================================================================
    // READ STORE (repository pour le modèle de lecture)
    // =========================================================================

    /** Stockage du Read Model (dénormalisé, optimisé pour les requêtes) */
    static class ReadStore {
        private final Map<String, ProductReadModel> products = new ConcurrentHashMap<>();

        /** Projeter : transformer le Write Model en Read Model */
        void project(Product product) {
            var readModel = new ProductReadModel(
                product.getId(),
                product.getNom(),
                product.getCategorie(),
                product.getPrix(),
                product.getStock(),
                product.isActif(),
                product.getStock() > 0,
                classifierPrix(product.getPrix()),
                product.getHistoriquePrix().size(),
                Instant.now()
            );
            products.put(product.getId(), readModel);
        }

        Optional<ProductReadModel> findById(String id) {
            return Optional.ofNullable(products.get(id));
        }

        List<ProductReadModel> search(Predicate<ProductReadModel> filtre) {
            return products.values().stream()
                .filter(filtre)
                .toList();
        }

        CatalogueStats getStats() {
            var all = products.values();
            var parCategorie = new HashMap<String, Integer>();
            all.forEach(p -> parCategorie.merge(p.categorie(), 1, Integer::sum));

            return new CatalogueStats(
                all.size(),
                (int) all.stream().filter(ProductReadModel::actif).count(),
                (int) all.stream().filter(ProductReadModel::enStock).count(),
                all.stream().mapToDouble(ProductReadModel::prix).average().orElse(0),
                parCategorie
            );
        }

        private String classifierPrix(double prix) {
            if (prix < 50) return "Budget";
            if (prix < 200) return "Standard";
            return "Premium";
        }
    }

    // =========================================================================
    // COMMAND HANDLERS (traitent les commandes)
    // =========================================================================

    /** Interface générique pour les handlers de commandes */
    interface CommandHandler<C extends Command> {
        void handle(C command);
    }

    /** Handler : création de produit */
    static class CreateProductCommandHandler implements CommandHandler<CreateProductCommand> {
        private final WriteStore writeStore;
        private final ReadStore readStore;

        CreateProductCommandHandler(WriteStore writeStore, ReadStore readStore) {
            this.writeStore = writeStore;
            this.readStore = readStore;
        }

        @Override
        public void handle(CreateProductCommand cmd) {
            // 1. Créer dans le Write Model (avec les règles métier)
            var product = new Product(cmd.productId(), cmd.nom(), cmd.categorie(), cmd.prix(), cmd.stock());
            writeStore.save(product);

            // 2. Projeter vers le Read Model (dénormalisé)
            readStore.project(product);

            System.out.println("   [Write] Produit créé : " + cmd.nom());
        }
    }

    /** Handler : mise à jour du prix */
    static class UpdatePriceCommandHandler implements CommandHandler<UpdatePriceCommand> {
        private final WriteStore writeStore;
        private final ReadStore readStore;

        UpdatePriceCommandHandler(WriteStore writeStore, ReadStore readStore) {
            this.writeStore = writeStore;
            this.readStore = readStore;
        }

        @Override
        public void handle(UpdatePriceCommand cmd) {
            var product = writeStore.findById(cmd.productId())
                .orElseThrow(() -> new IllegalArgumentException("Produit introuvable : " + cmd.productId()));

            // Règles métier dans le Write Model
            product.changerPrix(cmd.nouveauPrix(), cmd.motif());
            writeStore.save(product);

            // Re-projeter vers le Read Model
            readStore.project(product);

            System.out.printf("   [Write] Prix mis à jour : %s → %.2f EUR (%s)%n",
                cmd.productId(), cmd.nouveauPrix(), cmd.motif());
        }
    }

    /** Handler : désactivation */
    static class DeactivateProductCommandHandler implements CommandHandler<DeactivateProductCommand> {
        private final WriteStore writeStore;
        private final ReadStore readStore;

        DeactivateProductCommandHandler(WriteStore writeStore, ReadStore readStore) {
            this.writeStore = writeStore;
            this.readStore = readStore;
        }

        @Override
        public void handle(DeactivateProductCommand cmd) {
            var product = writeStore.findById(cmd.productId())
                .orElseThrow(() -> new IllegalArgumentException("Produit introuvable"));
            product.desactiver(cmd.raison());
            writeStore.save(product);
            readStore.project(product);
            System.out.println("   [Write] Produit désactivé : " + cmd.productId());
        }
    }

    // =========================================================================
    // QUERY HANDLERS (traitent les requêtes de lecture)
    // =========================================================================

    /** Interface générique pour les handlers de queries */
    interface QueryHandler<Q extends Query<R>, R> {
        R handle(Q query);
    }

    /** Handler : obtenir un produit */
    static class GetProductQueryHandler implements QueryHandler<GetProductQuery, ProductReadModel> {
        private final ReadStore readStore;

        GetProductQueryHandler(ReadStore readStore) {
            this.readStore = readStore;
        }

        @Override
        public ProductReadModel handle(GetProductQuery query) {
            return readStore.findById(query.productId())
                .orElseThrow(() -> new IllegalArgumentException("Produit introuvable"));
        }
    }

    /** Handler : rechercher des produits */
    static class SearchProductsQueryHandler implements QueryHandler<SearchProductsQuery, List<ProductReadModel>> {
        private final ReadStore readStore;

        SearchProductsQueryHandler(ReadStore readStore) {
            this.readStore = readStore;
        }

        @Override
        public List<ProductReadModel> handle(SearchProductsQuery query) {
            Predicate<ProductReadModel> filtre = p -> true;

            if (query.categorie() != null) {
                filtre = filtre.and(p -> query.categorie().equals(p.categorie()));
            }
            if (query.prixMin() != null) {
                filtre = filtre.and(p -> p.prix() >= query.prixMin());
            }
            if (query.prixMax() != null) {
                filtre = filtre.and(p -> p.prix() <= query.prixMax());
            }
            if (query.actifUniquement() != null && query.actifUniquement()) {
                filtre = filtre.and(ProductReadModel::actif);
            }
            return readStore.search(filtre);
        }
    }

    /** Handler : statistiques */
    static class GetCatalogueStatsQueryHandler implements QueryHandler<GetCatalogueStatsQuery, CatalogueStats> {
        private final ReadStore readStore;

        GetCatalogueStatsQueryHandler(ReadStore readStore) {
            this.readStore = readStore;
        }

        @Override
        public CatalogueStats handle(GetCatalogueStatsQuery query) {
            return readStore.getStats();
        }
    }

    // =========================================================================
    // BUS DE COMMANDES ET QUERIES
    // =========================================================================

    /** CommandBus : dispatche les commandes vers le bon handler */
    @SuppressWarnings("unchecked")
    static class CommandBus {
        private final Map<Class<?>, CommandHandler<?>> handlers = new HashMap<>();

        <C extends Command> void register(Class<C> commandType, CommandHandler<C> handler) {
            handlers.put(commandType, handler);
        }

        <C extends Command> void dispatch(C command) {
            var handler = (CommandHandler<C>) handlers.get(command.getClass());
            if (handler == null) {
                throw new IllegalArgumentException("Pas de handler pour : " + command.getClass().getSimpleName());
            }
            handler.handle(command);
        }
    }

    /** QueryBus : dispatche les queries vers le bon handler */
    @SuppressWarnings("unchecked")
    static class QueryBus {
        private final Map<Class<?>, QueryHandler<?, ?>> handlers = new HashMap<>();

        <Q extends Query<R>, R> void register(Class<Q> queryType, QueryHandler<Q, R> handler) {
            handlers.put(queryType, handler);
        }

        <Q extends Query<R>, R> R dispatch(Q query) {
            var handler = (QueryHandler<Q, R>) handlers.get(query.getClass());
            if (handler == null) {
                throw new IllegalArgumentException("Pas de handler pour : " + query.getClass().getSimpleName());
            }
            return handler.handle(query);
        }
    }

    // =========================================================================
    // POINT D'ENTRÉE
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  Demo 13 - CQRS (Command Query Responsibility Segregation)║");
        System.out.println("║  Séparer les chemins d'écriture et de lecture            ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        // --- Setup : stores séparés ---
        var writeStore = new WriteStore();
        var readStore = new ReadStore();

        // --- Setup : Command Bus ---
        var commandBus = new CommandBus();
        commandBus.register(CreateProductCommand.class, new CreateProductCommandHandler(writeStore, readStore));
        commandBus.register(UpdatePriceCommand.class, new UpdatePriceCommandHandler(writeStore, readStore));
        commandBus.register(DeactivateProductCommand.class, new DeactivateProductCommandHandler(writeStore, readStore));

        // --- Setup : Query Bus ---
        var queryBus = new QueryBus();
        queryBus.register(GetProductQuery.class, new GetProductQueryHandler(readStore));
        queryBus.register(SearchProductsQuery.class, new SearchProductsQueryHandler(readStore));
        queryBus.register(GetCatalogueStatsQuery.class, new GetCatalogueStatsQueryHandler(readStore));

        // =====================================================================
        // CÔTÉ ÉCRITURE (Commands)
        // =====================================================================
        System.out.println("\n📝 CÔTÉ ÉCRITURE : envoi de commandes via CommandBus");
        System.out.println("─".repeat(55));

        commandBus.dispatch(new CreateProductCommand(
            "PROD-001", "MacBook Pro 14\"", "Informatique", 2499.00, 15
        ));
        commandBus.dispatch(new CreateProductCommand(
            "PROD-002", "Clavier MX Keys", "Périphériques", 119.99, 50
        ));
        commandBus.dispatch(new CreateProductCommand(
            "PROD-003", "Câble USB-C", "Accessoires", 12.99, 200
        ));
        commandBus.dispatch(new CreateProductCommand(
            "PROD-004", "Écran 4K 27\"", "Informatique", 449.00, 8
        ));

        // Mise à jour de prix
        commandBus.dispatch(new UpdatePriceCommand(
            "PROD-002", 99.99, "Promotion printemps"
        ));

        // Désactivation
        commandBus.dispatch(new DeactivateProductCommand(
            "PROD-003", "Fin de vie du produit"
        ));

        // =====================================================================
        // CÔTÉ LECTURE (Queries)
        // =====================================================================
        System.out.println("\n📖 CÔTÉ LECTURE : requêtes via QueryBus");
        System.out.println("─".repeat(55));

        // Query 1 : obtenir un produit
        System.out.println("\n   🔍 Query: GetProductQuery(PROD-001)");
        var macbook = queryBus.dispatch(new GetProductQuery("PROD-001"));
        System.out.printf("   → %s | %.2f EUR | %s | En stock: %s%n",
            macbook.nom(), macbook.prix(), macbook.rangePrix(), macbook.enStock());

        // Query 2 : rechercher par catégorie
        System.out.println("\n   🔍 Query: SearchProductsQuery(categorie=Informatique)");
        var informatique = queryBus.dispatch(
            new SearchProductsQuery("Informatique", null, null, true)
        );
        informatique.forEach(p ->
            System.out.printf("   → %s | %.2f EUR | %s%n", p.nom(), p.prix(), p.rangePrix())
        );

        // Query 3 : rechercher par range de prix
        System.out.println("\n   🔍 Query: SearchProductsQuery(prixMax=200, actifUniquement=true)");
        var budget = queryBus.dispatch(
            new SearchProductsQuery(null, null, 200.0, true)
        );
        budget.forEach(p ->
            System.out.printf("   → %s | %.2f EUR | Actif: %s%n", p.nom(), p.prix(), p.actif())
        );

        // Query 4 : statistiques
        System.out.println("\n   🔍 Query: GetCatalogueStatsQuery()");
        var stats = queryBus.dispatch(new GetCatalogueStatsQuery());
        System.out.println("   → Total produits     : " + stats.totalProduits());
        System.out.println("   → Produits actifs    : " + stats.produitsActifs());
        System.out.println("   → Produits en stock  : " + stats.produitsEnStock());
        System.out.printf("   → Prix moyen         : %.2f EUR%n", stats.prixMoyen());
        System.out.println("   → Par catégorie      : " + stats.parCategorie());

        // =====================================================================
        // COMPARAISON WRITE vs READ MODEL
        // =====================================================================
        System.out.println("\n📊 COMPARAISON : Write Model vs Read Model");
        System.out.println("─".repeat(55));

        System.out.println("\n   Write Model (normalisé, pour le domaine) :");
        var writeProduct = writeStore.findById("PROD-002").orElseThrow();
        System.out.println("   → id         : " + writeProduct.getId());
        System.out.println("   → nom        : " + writeProduct.getNom());
        System.out.println("   → prix       : " + writeProduct.getPrix());
        System.out.println("   → actif      : " + writeProduct.isActif());
        System.out.println("   → historique  : " + writeProduct.getHistoriquePrix().size() + " changements");

        System.out.println("\n   Read Model (dénormalisé, pour les vues/API) :");
        var readProduct = readStore.findById("PROD-002").orElseThrow();
        System.out.println("   → productId  : " + readProduct.productId());
        System.out.println("   → nom        : " + readProduct.nom());
        System.out.println("   → prix       : " + readProduct.prix());
        System.out.println("   → rangePrix  : " + readProduct.rangePrix() + "  ← champ calculé/dénormalisé");
        System.out.println("   → enStock    : " + readProduct.enStock() + "  ← champ calculé/dénormalisé");
        System.out.println("   → nbChangements : " + readProduct.nombreChangementsPrix() + "  ← champ dénormalisé");

        // --- Résumé ---
        System.out.println("\n" + "═".repeat(55));
        System.out.println("📌 Points clés du CQRS :");
        System.out.println("   1. Commandes = intentions de modification (void)");
        System.out.println("   2. Queries = demandes de lecture (retournent des données)");
        System.out.println("   3. Write Model = normalisé, protège les invariants");
        System.out.println("   4. Read Model = dénormalisé, optimisé pour les vues");
        System.out.println("   5. CommandBus / QueryBus = découplage et extensibilité");
        System.out.println("   6. Se combine avec Event Sourcing (Demo 12)");
        System.out.println("═".repeat(55));
    }
}
