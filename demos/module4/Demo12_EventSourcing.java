package com.utopios.hexagonal.demos.module4;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Demo 12 - Event Sourcing avec Architecture Hexagonale
 *
 * L'Event Sourcing consiste à stocker les ÉVÉNEMENTS plutôt que l'état courant.
 * L'état d'un agrégat est reconstruit en rejouant ses événements.
 *
 * Avantages :
 * - Historique complet et immuable
 * - Audit trail naturel
 * - Possibilité de reconstruire l'état à n'importe quel moment
 * - Se combine parfaitement avec CQRS (voir Demo 13)
 *
 * En architecture hexagonale :
 * - DomainEvent = concept du domaine
 * - EventStore = port de sortie
 * - InMemoryEventStore = adapter
 */
public class Demo12_EventSourcing {

    // =========================================================================
    // ÉVÉNEMENTS DU DOMAINE
    // =========================================================================

    /** Interface scellée : tous les événements du domaine */
    sealed interface DomainEvent {
        String getAggregateId();
        Instant getTimestamp();
        String getEventType();
    }

    /** Événement : un compte bancaire a été créé */
    record AccountCreated(
        String aggregateId,
        Instant timestamp,
        String titulaire,
        String devise
    ) implements DomainEvent {
        @Override public String getAggregateId() { return aggregateId; }
        @Override public Instant getTimestamp() { return timestamp; }
        @Override public String getEventType() { return "AccountCreated"; }
    }

    /** Événement : de l'argent a été déposé */
    record MoneyDeposited(
        String aggregateId,
        Instant timestamp,
        double montant,
        String motif
    ) implements DomainEvent {
        @Override public String getAggregateId() { return aggregateId; }
        @Override public Instant getTimestamp() { return timestamp; }
        @Override public String getEventType() { return "MoneyDeposited"; }
    }

    /** Événement : de l'argent a été retiré */
    record MoneyWithdrawn(
        String aggregateId,
        Instant timestamp,
        double montant,
        String motif
    ) implements DomainEvent {
        @Override public String getAggregateId() { return aggregateId; }
        @Override public Instant getTimestamp() { return timestamp; }
        @Override public String getEventType() { return "MoneyWithdrawn"; }
    }

    /** Événement : le compte a été bloqué */
    record AccountBlocked(
        String aggregateId,
        Instant timestamp,
        String raison
    ) implements DomainEvent {
        @Override public String getAggregateId() { return aggregateId; }
        @Override public Instant getTimestamp() { return timestamp; }
        @Override public String getEventType() { return "AccountBlocked"; }
    }

    // =========================================================================
    // AGRÉGAT : BankAccount (reconstruit depuis les événements)
    // =========================================================================

    /**
     * L'agrégat BankAccount ne stocke PAS son état en base.
     * Il le reconstruit en rejouant les événements.
     *
     * Pattern "Event-Sourced Aggregate" :
     * 1. apply() produit de nouveaux événements (non committés)
     * 2. on() applique un événement à l'état interne
     * 3. reconstituer() rejoue l'historique complet
     */
    static final class BankAccount {

        // État interne (reconstruit depuis les événements)
        private String accountId;
        private String titulaire;
        private String devise;
        private double solde;
        private boolean bloque;
        private int version; // numéro de version pour l'optimistic locking

        // Événements non encore persistés
        private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

        /** Constructeur privé : utiliser les factory methods */
        private BankAccount() {
            this.solde = 0;
            this.bloque = false;
            this.version = 0;
        }

        // --- Factory : création depuis un nouvel événement ---

        /** Créer un nouveau compte (produit un événement AccountCreated) */
        static BankAccount creer(String accountId, String titulaire, String devise) {
            var account = new BankAccount();
            account.apply(new AccountCreated(accountId, Instant.now(), titulaire, devise));
            return account;
        }

        // --- Factory : reconstruction depuis l'historique ---

        /** Reconstruire un compte depuis son historique d'événements */
        static BankAccount reconstituer(List<DomainEvent> historique) {
            var account = new BankAccount();
            for (var event : historique) {
                account.on(event);
                account.version++;
            }
            return account;
        }

        // --- Commandes métier (produisent des événements) ---

        /** Déposer de l'argent */
        void deposer(double montant, String motif) {
            if (bloque) {
                throw new IllegalStateException("Compte bloqué : opération interdite");
            }
            if (montant <= 0) {
                throw new IllegalArgumentException("Le montant doit être positif");
            }
            apply(new MoneyDeposited(accountId, Instant.now(), montant, motif));
        }

        /** Retirer de l'argent */
        void retirer(double montant, String motif) {
            if (bloque) {
                throw new IllegalStateException("Compte bloqué : opération interdite");
            }
            if (montant <= 0) {
                throw new IllegalArgumentException("Le montant doit être positif");
            }
            if (montant > solde) {
                throw new IllegalStateException(
                    String.format("Solde insuffisant : %.2f disponible, %.2f demandé", solde, montant)
                );
            }
            apply(new MoneyWithdrawn(accountId, Instant.now(), montant, motif));
        }

        /** Bloquer le compte */
        void bloquer(String raison) {
            if (bloque) {
                throw new IllegalStateException("Compte déjà bloqué");
            }
            apply(new AccountBlocked(accountId, Instant.now(), raison));
        }

        // --- Application des événements à l'état interne ---

        /** Appliquer un événement (nouvelle commande → enregistre en uncommitted) */
        private void apply(DomainEvent event) {
            on(event);
            uncommittedEvents.add(event);
        }

        /** Mettre à jour l'état interne en fonction de l'événement */
        private void on(DomainEvent event) {
            if (event instanceof AccountCreated e) {
                this.accountId = e.aggregateId();
                this.titulaire = e.titulaire();
                this.devise = e.devise();
                this.solde = 0;
            } else if (event instanceof MoneyDeposited e) {
                this.solde += e.montant();
            } else if (event instanceof MoneyWithdrawn e) {
                this.solde -= e.montant();
            } else if (event instanceof AccountBlocked e) {
                this.bloque = true;
            }
        }

        // --- Accès aux événements non committés ---

        List<DomainEvent> getUncommittedEvents() {
            return Collections.unmodifiableList(uncommittedEvents);
        }

        void markEventsAsCommitted() {
            uncommittedEvents.clear();
        }

        // --- Accesseurs ---
        String getAccountId() { return accountId; }
        String getTitulaire() { return titulaire; }
        double getSolde() { return solde; }
        boolean isBloque() { return bloque; }
        int getVersion() { return version; }

        @Override
        public String toString() {
            return String.format("BankAccount{id='%s', titulaire='%s', solde=%.2f %s, bloqué=%s, version=%d}",
                accountId, titulaire, solde, devise, bloque, version);
        }
    }

    // =========================================================================
    // PORT : EventStore
    // =========================================================================

    /** Port de sortie : stockage des événements */
    interface EventStore {
        /** Sauvegarder les nouveaux événements d'un agrégat */
        void save(String aggregateId, List<DomainEvent> events, int expectedVersion);

        /** Charger tous les événements d'un agrégat */
        List<DomainEvent> load(String aggregateId);

        /** Charger tous les événements (tous agrégats confondus) */
        List<DomainEvent> loadAll();
    }

    // =========================================================================
    // ADAPTER : InMemoryEventStore
    // =========================================================================

    /** Adapter : event store en mémoire (en prod → EventStoreDB, Kafka, etc.) */
    static class InMemoryEventStore implements EventStore {

        // Simule un log d'événements append-only
        private final Map<String, List<DomainEvent>> store = new LinkedHashMap<>();
        // Versions pour l'optimistic locking
        private final Map<String, Integer> versions = new HashMap<>();

        @Override
        public void save(String aggregateId, List<DomainEvent> events, int expectedVersion) {
            int currentVersion = versions.getOrDefault(aggregateId, 0);

            // Optimistic locking : vérifier que personne n'a modifié l'agrégat
            if (currentVersion != expectedVersion) {
                throw new IllegalStateException(String.format(
                    "Conflit de version pour %s : attendu %d, actuel %d",
                    aggregateId, expectedVersion, currentVersion
                ));
            }

            // Append-only : on ajoute les événements au log
            store.computeIfAbsent(aggregateId, k -> new ArrayList<>()).addAll(events);
            versions.put(aggregateId, currentVersion + events.size());
        }

        @Override
        public List<DomainEvent> load(String aggregateId) {
            return Collections.unmodifiableList(
                store.getOrDefault(aggregateId, List.of())
            );
        }

        @Override
        public List<DomainEvent> loadAll() {
            return store.values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(DomainEvent::getTimestamp))
                .toList();
        }
    }

    // =========================================================================
    // SERVICE APPLICATIF (orchestre domaine + event store)
    // =========================================================================

    static class BankAccountService {
        private final EventStore eventStore;

        BankAccountService(EventStore eventStore) {
            this.eventStore = eventStore;
        }

        /** Créer un nouveau compte */
        BankAccount creerCompte(String id, String titulaire, String devise) {
            var account = BankAccount.creer(id, titulaire, devise);
            eventStore.save(id, account.getUncommittedEvents(), 0);
            account.markEventsAsCommitted();
            return account;
        }

        /** Charger un compte depuis l'event store */
        BankAccount chargerCompte(String id) {
            var events = eventStore.load(id);
            if (events.isEmpty()) {
                throw new IllegalArgumentException("Compte introuvable : " + id);
            }
            return BankAccount.reconstituer(events);
        }

        /** Déposer de l'argent */
        void deposer(String accountId, double montant, String motif) {
            var account = chargerCompte(accountId);
            account.deposer(montant, motif);
            eventStore.save(accountId, account.getUncommittedEvents(), account.getVersion());
            account.markEventsAsCommitted();
        }

        /** Retirer de l'argent */
        void retirer(String accountId, double montant, String motif) {
            var account = chargerCompte(accountId);
            account.retirer(montant, motif);
            eventStore.save(accountId, account.getUncommittedEvents(), account.getVersion());
            account.markEventsAsCommitted();
        }
    }

    // =========================================================================
    // POINT D'ENTRÉE
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  Demo 12 - Event Sourcing                               ║");
        System.out.println("║  L'état se reconstruit depuis les événements             ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        var eventStore = new InMemoryEventStore();
        var service = new BankAccountService(eventStore);

        // --- Étape 1 : Créer un compte ---
        System.out.println("\n📌 Étape 1 : Création du compte");
        System.out.println("─".repeat(55));
        var account = service.creerCompte("ACC-001", "Alice Martin", "EUR");
        System.out.println("   Compte créé : " + account);

        // --- Étape 2 : Opérations successives ---
        System.out.println("\n📌 Étape 2 : Opérations bancaires");
        System.out.println("─".repeat(55));

        service.deposer("ACC-001", 1000, "Virement salaire");
        System.out.println("   Dépôt de 1000 EUR (salaire)");

        service.deposer("ACC-001", 500, "Prime trimestrielle");
        System.out.println("   Dépôt de 500 EUR (prime)");

        service.retirer("ACC-001", 150, "Courses alimentaires");
        System.out.println("   Retrait de 150 EUR (courses)");

        service.retirer("ACC-001", 89.99, "Abonnement annuel");
        System.out.println("   Retrait de 89.99 EUR (abonnement)");

        // --- Étape 3 : Vérifier l'état actuel ---
        System.out.println("\n📌 Étape 3 : État reconstruit depuis les événements");
        System.out.println("─".repeat(55));
        var accountReloaded = service.chargerCompte("ACC-001");
        System.out.println("   " + accountReloaded);
        System.out.printf("   Solde calculé : %.2f EUR%n", accountReloaded.getSolde());

        // --- Étape 4 : Afficher l'historique complet ---
        System.out.println("\n📌 Étape 4 : Historique complet des événements");
        System.out.println("─".repeat(55));
        var events = eventStore.load("ACC-001");
        for (int i = 0; i < events.size(); i++) {
            var event = events.get(i);
            String detail;
            if (event instanceof AccountCreated e) {
                detail = String.format("Compte créé pour %s (%s)", e.titulaire(), e.devise());
            } else if (event instanceof MoneyDeposited e) {
                detail = String.format("+%.2f EUR (%s)", e.montant(), e.motif());
            } else if (event instanceof MoneyWithdrawn e) {
                detail = String.format("-%.2f EUR (%s)", e.montant(), e.motif());
            } else if (event instanceof AccountBlocked e) {
                detail = String.format("BLOQUE : %s", e.raison());
            } else {
                detail = "Événement inconnu";
            }
            System.out.printf("   [%d] %s → %s%n", i + 1, event.getEventType(), detail);
        }

        // --- Étape 5 : Reconstruction à un instant T ---
        System.out.println("\n📌 Étape 5 : Reconstruction partielle (time travel)");
        System.out.println("─".repeat(55));
        System.out.println("   Replay des 3 premiers événements uniquement :");

        var partialEvents = events.subList(0, 3);
        var snapshotAccount = BankAccount.reconstituer(partialEvents);
        System.out.printf("   Solde après 3 événements : %.2f EUR%n", snapshotAccount.getSolde());
        System.out.println("   (Création + 1000 salaire + 500 prime = 1500 EUR)");

        // --- Étape 6 : Second compte pour montrer l'isolation ---
        System.out.println("\n📌 Étape 6 : Isolation des agrégats");
        System.out.println("─".repeat(55));
        service.creerCompte("ACC-002", "Bob Leroy", "EUR");
        service.deposer("ACC-002", 2000, "Virement initial");
        var bob = service.chargerCompte("ACC-002");
        System.out.println("   Compte Bob : " + bob);
        System.out.println("   Événements ACC-001 : " + eventStore.load("ACC-001").size());
        System.out.println("   Événements ACC-002 : " + eventStore.load("ACC-002").size());
        System.out.println("   Total événements dans le store : " + eventStore.loadAll().size());

        // --- Résumé ---
        System.out.println("\n" + "═".repeat(55));
        System.out.println("📌 Points clés de l'Event Sourcing :");
        System.out.println("   1. On stocke les FAITS (événements), pas l'état");
        System.out.println("   2. L'état se reconstruit en rejouant les événements");
        System.out.println("   3. Historique complet et immuable (audit trail)");
        System.out.println("   4. Time travel : reconstruire l'état à n'importe quand");
        System.out.println("   5. EventStore = port de sortie (adapter interchangeable)");
        System.out.println("   6. Se combine avec CQRS pour les lectures optimisées");
        System.out.println("═".repeat(55));
    }
}
