package com.utopios.hexagonal.demos.module4;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Demo 14 - Documentation Vivante (Living Documentation)
 *
 * La documentation vivante est générée DEPUIS le code, pas écrite à côté.
 * Elle ne peut pas devenir obsolète car elle EST le code.
 *
 * Trois piliers :
 * 1. Tests comme documentation (BDD, noms expressifs) → voir Demo 10
 * 2. Architecture as Code (ArchUnit) → règles vérifiables
 * 3. ADR (Architecture Decision Records) → décisions tracées
 *
 * En architecture hexagonale, on peut vérifier automatiquement :
 * - Le domaine n'importe pas d'infrastructure
 * - Les ports sont des interfaces
 * - Les adapters implémentent des ports
 * - Les dépendances vont de l'extérieur vers l'intérieur
 */
public class Demo14_DocumentationVivante {

    // =========================================================================
    // 1. RÈGLES ARCHUNIT (Architecture as Code)
    // =========================================================================

    /*
     * En production avec ArchUnit, on écrirait :
     *
     * @AnalyzeClasses(packages = "com.utopios.hexagonal")
     * class ArchitectureHexagonaleRulesTest {
     *
     *     @ArchTest
     *     static final ArchRule le_domaine_ne_depend_pas_de_linfrastructure =
     *         noClasses()
     *             .that().resideInAPackage("..domain..")
     *             .should().dependOnClassesThat()
     *             .resideInAnyPackage("..infrastructure..", "..adapter..");
     *
     *     @ArchTest
     *     static final ArchRule les_ports_sont_des_interfaces =
     *         classes()
     *             .that().resideInAPackage("..port..")
     *             .should().beInterfaces();
     *
     *     @ArchTest
     *     static final ArchRule les_adapters_implementent_des_ports =
     *         classes()
     *             .that().resideInAPackage("..adapter..")
     *             .should().implement(classNameMatching(".*Port"));
     *
     *     @ArchTest
     *     static final ArchRule pas_de_spring_dans_le_domaine =
     *         noClasses()
     *             .that().resideInAPackage("..domain..")
     *             .should().dependOnClassesThat()
     *             .resideInAnyPackage("org.springframework..");
     *
     *     @ArchTest
     *     static final ArchRule architecture_en_couches =
     *         layeredArchitecture()
     *             .consideringAllDependencies()
     *             .layer("Domain").definedBy("..domain..")
     *             .layer("Application").definedBy("..application..")
     *             .layer("Infrastructure").definedBy("..infrastructure..")
     *             .whereLayer("Domain").mayNotAccessAnyLayer()
     *             .whereLayer("Application").mayOnlyAccessLayers("Domain")
     *             .whereLayer("Infrastructure").mayOnlyAccessLayers("Domain", "Application");
     * }
     */

    // =========================================================================
    // SIMULATION D'ANALYSE DE PACKAGES
    // =========================================================================

    /** Représente une classe dans le projet (simulation) */
    record ClasseProjet(
        String nomComplet,
        String packageName,
        String nomSimple,
        boolean estInterface,
        List<String> imports,
        List<String> implementes
    ) {
        String couche() {
            if (packageName.contains("domain")) return "Domain";
            if (packageName.contains("application")) return "Application";
            if (packageName.contains("port")) return "Port";
            if (packageName.contains("adapter") || packageName.contains("infrastructure")) return "Infrastructure";
            return "Autre";
        }
    }

    /** Règle d'architecture vérifiable */
    record RegleArchitecture(String nom, String description, java.util.function.Predicate<List<ClasseProjet>> verification) {

        ResultatVerification verifier(List<ClasseProjet> classes) {
            boolean respectee = verification.test(classes);
            return new ResultatVerification(nom, description, respectee);
        }
    }

    record ResultatVerification(String regle, String description, boolean respectee) {}

    /** Construire un projet simulé pour démontrer les vérifications */
    static List<ClasseProjet> construireProjetSimule() {
        return List.of(
            // Domaine
            new ClasseProjet(
                "com.utopios.hexagonal.domain.model.Order",
                "com.utopios.hexagonal.domain.model", "Order", false,
                List.of("java.util.List", "java.time.LocalDateTime"),
                List.of()
            ),
            new ClasseProjet(
                "com.utopios.hexagonal.domain.model.Money",
                "com.utopios.hexagonal.domain.model", "Money", false,
                List.of("java.math.BigDecimal"),
                List.of()
            ),
            // Ports
            new ClasseProjet(
                "com.utopios.hexagonal.domain.port.OrderRepository",
                "com.utopios.hexagonal.domain.port", "OrderRepository", true,
                List.of("com.utopios.hexagonal.domain.model.Order"),
                List.of()
            ),
            new ClasseProjet(
                "com.utopios.hexagonal.domain.port.NotificationPort",
                "com.utopios.hexagonal.domain.port", "NotificationPort", true,
                List.of(),
                List.of()
            ),
            // Application
            new ClasseProjet(
                "com.utopios.hexagonal.application.CreateOrderUseCase",
                "com.utopios.hexagonal.application", "CreateOrderUseCase", false,
                List.of("com.utopios.hexagonal.domain.model.Order",
                         "com.utopios.hexagonal.domain.port.OrderRepository"),
                List.of()
            ),
            // Adapters (Infrastructure)
            new ClasseProjet(
                "com.utopios.hexagonal.infrastructure.adapter.JpaOrderRepository",
                "com.utopios.hexagonal.infrastructure.adapter", "JpaOrderRepository", false,
                List.of("com.utopios.hexagonal.domain.port.OrderRepository",
                         "org.springframework.stereotype.Repository",
                         "javax.persistence.EntityManager"),
                List.of("OrderRepository")
            ),
            new ClasseProjet(
                "com.utopios.hexagonal.infrastructure.adapter.EmailNotificationAdapter",
                "com.utopios.hexagonal.infrastructure.adapter", "EmailNotificationAdapter", false,
                List.of("com.utopios.hexagonal.domain.port.NotificationPort",
                         "org.springframework.mail.MailSender"),
                List.of("NotificationPort")
            ),
            // Classe en violation (pour la démo)
            new ClasseProjet(
                "com.utopios.hexagonal.domain.model.BadEntity",
                "com.utopios.hexagonal.domain.model", "BadEntity", false,
                List.of("org.springframework.stereotype.Component", "javax.persistence.Entity"),
                List.of()
            )
        );
    }

    /** Définir les règles d'architecture hexagonale */
    static List<RegleArchitecture> definirRegles() {
        return List.of(
            new RegleArchitecture(
                "R1 - Isolation du domaine",
                "Le domaine ne doit pas importer de frameworks (Spring, JPA, etc.)",
                classes -> classes.stream()
                    .filter(c -> c.couche().equals("Domain"))
                    .noneMatch(c -> c.imports().stream().anyMatch(i ->
                        i.startsWith("org.springframework") ||
                        i.startsWith("javax.persistence") ||
                        i.startsWith("jakarta.persistence")
                    ))
            ),
            new RegleArchitecture(
                "R2 - Ports = Interfaces",
                "Toutes les classes dans les packages 'port' doivent être des interfaces",
                classes -> classes.stream()
                    .filter(c -> c.couche().equals("Port"))
                    .allMatch(ClasseProjet::estInterface)
            ),
            new RegleArchitecture(
                "R3 - Adapters implémentent des ports",
                "Les adapters d'infrastructure doivent implémenter au moins un port",
                classes -> classes.stream()
                    .filter(c -> c.couche().equals("Infrastructure"))
                    .filter(c -> !c.estInterface())
                    .allMatch(c -> !c.implementes().isEmpty())
            ),
            new RegleArchitecture(
                "R4 - Application ne dépend pas de l'infrastructure",
                "La couche application ne doit pas importer depuis infrastructure",
                classes -> classes.stream()
                    .filter(c -> c.couche().equals("Application"))
                    .noneMatch(c -> c.imports().stream().anyMatch(i -> i.contains("infrastructure")))
            ),
            new RegleArchitecture(
                "R5 - Domaine sans dépendance externe",
                "Le domaine n'importe que du java.* standard",
                classes -> classes.stream()
                    .filter(c -> c.couche().equals("Domain"))
                    .allMatch(c -> c.imports().stream().allMatch(i ->
                        i.startsWith("java.") || i.startsWith("com.utopios.hexagonal.domain")
                    ))
            )
        );
    }

    // =========================================================================
    // 2. ADR (Architecture Decision Records)
    // =========================================================================

    /** Un ADR (Architecture Decision Record) */
    record ADR(
        int numero,
        String titre,
        LocalDate date,
        String statut,     // "Acceptée", "Remplacée", "Dépréciée"
        String contexte,
        String decision,
        String consequences
    ) {
        String format() {
            return """
                ┌─────────────────────────────────────────────────────────┐
                │  ADR-%03d : %s
                │  Date : %s | Statut : %s
                ├─────────────────────────────────────────────────────────┤
                │  CONTEXTE
                │  %s
                │
                │  DÉCISION
                │  %s
                │
                │  CONSÉQUENCES
                │  %s
                └─────────────────────────────────────────────────────────┘
                """.formatted(numero, titre, date, statut,
                    indent(contexte), indent(decision), indent(consequences));
        }

        private String indent(String text) {
            return text.replace("\n", "\n│  ");
        }
    }

    /** Collection d'ADRs du projet */
    static List<ADR> getADRs() {
        return List.of(
            new ADR(1, "Adoption de l'Architecture Hexagonale",
                LocalDate.of(2024, 1, 15), "Acceptée",
                "Le projet doit être maintenable à long terme.\n" +
                "Les frameworks peuvent changer (Spring → Quarkus).\n" +
                "Plusieurs équipes travaillent sur le même domaine.",
                "Adopter l'architecture hexagonale (Ports & Adapters).\n" +
                "Le domaine métier est isolé au centre.\n" +
                "Toute dépendance externe passe par un port.",
                "+ Domaine testable sans infrastructure\n" +
                "+ Remplacement facile des technologies\n" +
                "- Courbe d'apprentissage pour l'équipe\n" +
                "- Plus de code boilerplate (mappers, DTOs)"
            ),
            new ADR(2, "Event Sourcing pour le module Comptabilité",
                LocalDate.of(2024, 3, 20), "Acceptée",
                "Le module comptabilité nécessite un audit trail complet.\n" +
                "Les régulateurs exigent la traçabilité de chaque opération.\n" +
                "Il faut pouvoir reconstruire l'état à une date donnée.",
                "Utiliser l'Event Sourcing pour les agrégats du domaine comptable.\n" +
                "Stocker les événements dans un EventStore dédié.\n" +
                "Combiner avec CQRS pour les lectures.",
                "+ Audit trail natif et immuable\n" +
                "+ Time travel pour les investigations\n" +
                "- Complexité accrue (projection, eventual consistency)\n" +
                "- Nécessite une formation de l'équipe"
            ),
            new ADR(3, "Utilisation de records Java pour les Value Objects",
                LocalDate.of(2024, 2, 10), "Acceptée",
                "Les Value Objects du domaine doivent être immuables.\n" +
                "L'équité par valeur est essentielle.\n" +
                "Java 17+ est la version minimale du projet.",
                "Utiliser les records Java pour tous les Value Objects.\n" +
                "Les entities restent des classes standard (état mutable).\n" +
                "Les DTOs utilisent aussi des records.",
                "+ Moins de code boilerplate\n" +
                "+ Immuabilité garantie par le compilateur\n" +
                "+ equals/hashCode/toString automatiques\n" +
                "- Pas d'héritage possible (sealed interfaces à la place)"
            )
        );
    }

    // =========================================================================
    // 3. RAPPORT D'ARCHITECTURE
    // =========================================================================

    /** Générer un rapport d'architecture depuis l'analyse du code */
    static void genererRapport(List<ClasseProjet> classes, List<RegleArchitecture> regles) {
        System.out.println("\n📊 RAPPORT D'ARCHITECTURE AUTOMATISÉ");
        System.out.println("═".repeat(58));
        System.out.println("   Généré le : " + LocalDate.now());
        System.out.println("   Nombre de classes analysées : " + classes.size());

        // Distribution par couche
        System.out.println("\n   📦 Distribution par couche :");
        var parCouche = classes.stream().collect(Collectors.groupingBy(ClasseProjet::couche));
        parCouche.forEach((couche, liste) -> {
            System.out.printf("      %-15s : %d classes%n", couche, liste.size());
            liste.forEach(c -> {
                String type = c.estInterface() ? "interface" : "class";
                System.out.printf("         %s %s%n", type, c.nomSimple());
            });
        });

        // Vérification des règles
        System.out.println("\n   🔍 Vérification des règles d'architecture :");
        System.out.println("   " + "─".repeat(55));

        int total = 0;
        int respectees = 0;
        var violations = new ArrayList<String>();

        for (var regle : regles) {
            total++;
            var resultat = regle.verifier(classes);
            if (resultat.respectee()) {
                respectees++;
                System.out.println("   ✅ " + resultat.regle());
                System.out.println("      " + resultat.description());
            } else {
                System.out.println("   ❌ " + resultat.regle());
                System.out.println("      " + resultat.description());
                violations.add(resultat.regle());
            }
        }

        // Score de conformité
        System.out.println("\n   " + "─".repeat(55));
        double score = (double) respectees / total * 100;
        System.out.printf("   📈 Score de conformité : %.0f%% (%d/%d règles respectées)%n",
            score, respectees, total);

        if (!violations.isEmpty()) {
            System.out.println("\n   ⚠️  Violations détectées :");
            violations.forEach(v -> System.out.println("      - " + v));
            System.out.println("\n   💡 Conseil : la classe BadEntity dans le domaine importe");
            System.out.println("      Spring et JPA. Elle doit être déplacée vers l'infrastructure");
            System.out.println("      ou nettoyée de ses imports de frameworks.");
        }

        // Graphe de dépendances simplifié
        System.out.println("\n   🔗 Graphe de dépendances (simplifié) :");
        System.out.println("   ┌──────────────────────────────────────────┐");
        System.out.println("   │           Infrastructure                  │");
        System.out.println("   │    ┌──────────┐    ┌──────────────┐      │");
        System.out.println("   │    │ JpaOrder │    │ EmailNotif   │      │");
        System.out.println("   │    │ Repo     │    │ Adapter      │      │");
        System.out.println("   │    └────┬─────┘    └──────┬───────┘      │");
        System.out.println("   │         │                 │              │");
        System.out.println("   │    implements         implements         │");
        System.out.println("   │         │                 │              │");
        System.out.println("   ├─────────┼─────────────────┼──────────────┤");
        System.out.println("   │         ▼                 ▼              │");
        System.out.println("   │  ┌─────────────┐  ┌──────────────┐      │");
        System.out.println("   │  │ OrderRepo   │  │ Notification │ Port │");
        System.out.println("   │  │ (port)      │  │ Port         │      │");
        System.out.println("   │  └──────▲──────┘  └──────▲───────┘      │");
        System.out.println("   │         │                 │              │");
        System.out.println("   │         uses              uses           │");
        System.out.println("   │         │                 │              │");
        System.out.println("   │    ┌────┴─────────────────┴────┐         │");
        System.out.println("   │    │   CreateOrderUseCase      │ App    │");
        System.out.println("   │    └────────────┬──────────────┘         │");
        System.out.println("   │                 │                        │");
        System.out.println("   │                uses                      │");
        System.out.println("   │                 │                        │");
        System.out.println("   │    ┌────────────▼──────────────┐         │");
        System.out.println("   │    │  Order, Money (domaine)   │ Domain │");
        System.out.println("   │    └──────────────────────────-┘         │");
        System.out.println("   └──────────────────────────────────────────┘");
    }

    // =========================================================================
    // POINT D'ENTRÉE
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  Demo 14 - Documentation Vivante                        ║");
        System.out.println("║  Le code EST la documentation                            ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        // --- 1. Règles ArchUnit ---
        System.out.println("\n📐 1. ARCHUNIT : Règles d'Architecture as Code");
        System.out.println("─".repeat(58));
        System.out.println("   En production, ces règles s'exécutent dans le build Maven/Gradle.");
        System.out.println("   Si une règle est violée → le build échoue.");
        System.out.println("   La documentation architecturale est VIVANTE car elle est du CODE.\n");

        var classes = construireProjetSimule();
        var regles = definirRegles();
        genererRapport(classes, regles);

        // --- 2. ADRs ---
        System.out.println("\n\n📜 2. ADR : Architecture Decision Records");
        System.out.println("─".repeat(58));
        System.out.println("   Chaque décision architecturale importante est documentée.");
        System.out.println("   Les ADRs sont versionnés avec le code (dans /docs/adr/).\n");

        for (var adr : getADRs()) {
            System.out.println(adr.format());
        }

        // --- 3. Annotations de documentation ---
        System.out.println("\n📝 3. ANNOTATIONS DE DOCUMENTATION");
        System.out.println("─".repeat(58));
        System.out.println("   On peut créer des annotations custom pour documenter le code :\n");

        System.out.println("""
               // Annotations pour documenter l'architecture hexagonale
               @Target(ElementType.TYPE)
               @Retention(RetentionPolicy.RUNTIME)
               public @interface DomainEntity {
                   String description() default "";
               }

               @Target(ElementType.TYPE)
               @Retention(RetentionPolicy.RUNTIME)
               public @interface Port {
                   PortType type();  // INPUT ou OUTPUT
                   String description() default "";
               }

               @Target(ElementType.TYPE)
               @Retention(RetentionPolicy.RUNTIME)
               public @interface Adapter {
                   String forPort();
                   AdapterType type();  // PRIMARY ou SECONDARY
               }

               // Utilisation :
               @DomainEntity(description = "Commande client avec ses lignes")
               public class Order { ... }

               @Port(type = INPUT, description = "Cas d'usage de création de commande")
               public interface CreateOrderUseCase { ... }

               @Adapter(forPort = "OrderRepository", type = SECONDARY)
               public class JpaOrderRepository implements OrderRepository { ... }
            """);

        System.out.println("   Ces annotations peuvent être scannées pour générer :");
        System.out.println("   - Un diagramme de l'architecture");
        System.out.println("   - Un glossaire du domaine");
        System.out.println("   - Une matrice de dépendances ports/adapters\n");

        // --- Résumé ---
        System.out.println("═".repeat(58));
        System.out.println("📌 Points clés de la documentation vivante :");
        System.out.println("   1. Le code EST la documentation (pas de wiki obsolète)");
        System.out.println("   2. ArchUnit = règles d'architecture dans le build");
        System.out.println("   3. ADR = décisions architecturales versionnées");
        System.out.println("   4. BDD/Gherkin = spécifications exécutables");
        System.out.println("   5. Annotations custom = métadonnées exploitables");
        System.out.println("   6. Si le build passe → l'architecture est conforme");
        System.out.println("═".repeat(58));
    }
}
