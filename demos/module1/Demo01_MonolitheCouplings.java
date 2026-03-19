/Users/ihababadi/Library/CloudStorage/OneDrive-utopios/supports_markdown/theme/architecture_hexagonale/demos/module1/Demo02_LayeredVsHexagonal.javapackage com.utopios.hexagonal.demos.module1;

/**
 * DEMO 01 - Le Monolithe et ses couplages
 * =========================================
 *
 * Cette demo illustre une approche CRUD monolithique classique
 * ou toutes les responsabilites sont melangees dans un seul service.
 *
 * Problemes demontres :
 *   1. Couplage fort avec la persistance (simulation JPA)
 *   2. Couplage fort avec les notifications (envoi email direct)
 *   3. Couplage fort avec l'infrastructure (logging direct)
 *   4. Impossible de tester unitairement sans base de donnees
 *   5. Impossible de changer de technologie sans tout modifier
 *
 * A executer : javac Demo01_MonolitheCouplings.java && java -cp . com.utopios.hexagonal.demos.module1.Demo01_MonolitheCouplings
 */
public class Demo01_MonolitheCouplings {

    // ========================================================================
    // PROBLEME 1 : Le modele est directement lie a la persistance
    // En vrai JPA, on aurait @Entity, @Id, @Column...
    // Le domaine metier ne devrait pas connaitre JPA.
    // ========================================================================
    static class Book {
        // Simulation @Id @GeneratedValue
        private Long id;
        // Simulation @Column(nullable = false)
        private String title;
        // Simulation @Column
        private String author;
        // Simulation @Column
        private double price;

        Book(Long id, String title, String author, double price) {
            this.id = id;
            this.title = title;
            this.author = author;
            this.price = price;
        }

        Long getId() { return id; }
        String getTitle() { return title; }
        String getAuthor() { return author; }
        double getPrice() { return price; }
        void setPrice(double price) { this.price = price; }

        @Override
        public String toString() {
            return "Book{id=%d, title='%s', author='%s', price=%.2f}"
                    .formatted(id, title, author, price);
        }
    }

    // ========================================================================
    // PROBLEME 2 : Simulation d'un EntityManager JPA
    // Le service metier utilise directement l'API de persistance.
    // On ne peut pas tester sans demarrer une base de donnees.
    // ========================================================================
    static class FakeEntityManager {
        private final java.util.Map<Long, Book> storage = new java.util.HashMap<>();
        private long sequence = 0;

        void persist(Book book) {
            sequence++;
            // Simulation de l'insertion en base
            var saved = new Book(sequence, book.getTitle(), book.getAuthor(), book.getPrice());
            storage.put(sequence, saved);
            System.out.println("  [EntityManager] INSERT INTO books VALUES (%d, '%s', '%s', %.2f)"
                    .formatted(sequence, book.getTitle(), book.getAuthor(), book.getPrice()));
        }

        Book find(Long id) {
            System.out.println("  [EntityManager] SELECT * FROM books WHERE id = " + id);
            return storage.get(id);
        }

        java.util.List<Book> findAll() {
            System.out.println("  [EntityManager] SELECT * FROM books");
            return new java.util.ArrayList<>(storage.values());
        }
    }

    // ========================================================================
    // PROBLEME 3 : Simulation d'un service d'envoi d'emails
    // Le service metier appelle directement l'infrastructure email.
    // Si on veut passer a Slack ou SMS, il faut modifier le service metier.
    // ========================================================================
    static class SmtpEmailSender {
        void sendEmail(String to, String subject, String body) {
            System.out.println("  [SMTP] Envoi email a %s | Sujet: %s | Corps: %s"
                    .formatted(to, subject, body));
        }
    }

    // ========================================================================
    // LE SERVICE MONOLITHIQUE - Tous les problemes concentres ici
    //
    // Ce service viole plusieurs principes :
    //   - SRP : il gere la logique metier, la persistance ET les notifications
    //   - DIP : il depend de classes concretes, pas d'abstractions
    //   - OCP : pour ajouter un nouveau canal de notification, il faut modifier ce code
    //
    // Consequences :
    //   - Test unitaire impossible sans base de donnees et serveur SMTP
    //   - Changement de technologie = refactoring massif
    //   - Chaque modification risque de casser tout le systeme
    // ========================================================================
    static class BookService {

        // COUPLAGE FORT : dependance directe vers l'infrastructure
        private final FakeEntityManager entityManager = new FakeEntityManager();
        // COUPLAGE FORT : dependance directe vers le systeme d'email
        private final SmtpEmailSender emailSender = new SmtpEmailSender();
        // COUPLAGE FORT : l'adresse admin est codee en dur
        private static final String ADMIN_EMAIL = "admin@librairie.com";

        /**
         * Creer un livre - Remarquez comment la logique metier, la persistance
         * et la notification sont completement melangees.
         */
        void createBook(String title, String author, double price) {
            // --- Validation metier ---
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("Le titre ne peut pas etre vide");
            }
            if (price < 0) {
                throw new IllegalArgumentException("Le prix ne peut pas etre negatif");
            }

            // COUPLAGE : appel direct a la persistance
            var book = new Book(null, title, author, price);
            entityManager.persist(book);

            // COUPLAGE : logging direct (en vrai on aurait log4j, slf4j...)
            System.out.println("  [LOG] Livre cree : " + title + " par " + author);

            // COUPLAGE : notification directe par email
            emailSender.sendEmail(
                    ADMIN_EMAIL,
                    "Nouveau livre ajoute",
                    "Le livre '%s' de %s a ete ajoute au catalogue (%.2f EUR)"
                            .formatted(title, author, price)
            );
        }

        /**
         * Appliquer une remise - La regle metier (max 50%) est noyee
         * au milieu du code d'infrastructure.
         */
        void applyDiscount(Long bookId, double discountPercent) {
            // COUPLAGE : acces direct a la persistance
            var book = entityManager.find(bookId);
            if (book == null) {
                throw new IllegalArgumentException("Livre introuvable : " + bookId);
            }

            // Regle metier noyee dans le code d'infrastructure
            if (discountPercent > 50) {
                throw new IllegalArgumentException("Remise maximale : 50%");
            }

            double newPrice = book.getPrice() * (1 - discountPercent / 100);
            book.setPrice(newPrice);

            // COUPLAGE : logging direct
            System.out.println("  [LOG] Remise de %.0f%% appliquee sur '%s' -> nouveau prix : %.2f EUR"
                    .formatted(discountPercent, book.getTitle(), newPrice));

            // COUPLAGE : notification directe
            emailSender.sendEmail(
                    ADMIN_EMAIL,
                    "Remise appliquee",
                    "Remise de %.0f%% sur '%s' - Nouveau prix : %.2f EUR"
                            .formatted(discountPercent, book.getTitle(), newPrice)
            );
        }

        /**
         * Lister les livres - Meme une simple lecture
         * est couplee a l'EntityManager.
         */
        java.util.List<Book> listBooks() {
            // COUPLAGE : impossible de lire depuis un cache, un fichier,
            // ou une API externe sans modifier ce code
            return entityManager.findAll();
        }
    }

    // ========================================================================
    // POINT D'ENTREE - Execution de la demo
    // ========================================================================
    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("DEMO 01 : Le Monolithe et ses Couplages");
        System.out.println("=".repeat(70));
        System.out.println();

        var service = new BookService();

        // --- Scenario 1 : Creation de livres ---
        System.out.println(">>> Scenario 1 : Creer des livres");
        System.out.println("-".repeat(40));
        service.createBook("Clean Architecture", "Robert C. Martin", 35.99);
        System.out.println();
        service.createBook("Domain-Driven Design", "Eric Evans", 45.50);
        System.out.println();

        // --- Scenario 2 : Appliquer une remise ---
        System.out.println(">>> Scenario 2 : Appliquer une remise");
        System.out.println("-".repeat(40));
        service.applyDiscount(1L, 20);
        System.out.println();

        // --- Scenario 3 : Lister les livres ---
        System.out.println(">>> Scenario 3 : Lister les livres");
        System.out.println("-".repeat(40));
        var books = service.listBooks();
        books.forEach(b -> System.out.println("  -> " + b));
        System.out.println();

        // --- Bilan ---
        System.out.println("=".repeat(70));
        System.out.println("BILAN - Problemes identifies dans ce monolithe :");
        System.out.println("-".repeat(70));
        System.out.println("  1. BookService cree lui-meme ses dependances (new)");
        System.out.println("  2. Logique metier melee a la persistance (EntityManager)");
        System.out.println("  3. Logique metier melee aux notifications (SmtpEmailSender)");
        System.out.println("  4. Impossible de tester sans infrastructure reelle");
        System.out.println("  5. Changer d'email vers Slack = modifier BookService");
        System.out.println("  6. Changer de BDD = modifier BookService");
        System.out.println();
        System.out.println("  --> Solution : Architecture Hexagonale (voir Demo 02 et 03)");
        System.out.println("=".repeat(70));
    }
}
