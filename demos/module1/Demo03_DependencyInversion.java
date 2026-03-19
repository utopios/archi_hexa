package com.utopios.hexagonal.demos.module1;

import java.util.ArrayList;
import java.util.List;

/**
 * DEMO 03 - Inversion de Dependance (DIP) en pratique
 * =====================================================
 *
 * Demonstration concrete du Dependency Inversion Principle :
 *   "Les modules de haut niveau ne doivent pas dependre des modules
 *    de bas niveau. Les deux doivent dependre d'abstractions."
 *
 * On cree :
 *   - Un PORT : NotificationSender (interface du domaine)
 *   - Trois ADAPTATEURS : Email, SMS, InMemory (pour les tests)
 *   - Un USE CASE : OrderUseCase qui ne connait que le port
 *   - Un main() qui assemble le tout SANS framework (pur Java)
 *
 * Cela prouve que le DIP fonctionne independamment de Spring ou
 * tout autre framework d'injection.
 *
 * A executer : javac Demo03_DependencyInversion.java && java -cp . com.utopios.hexagonal.demos.module1.Demo03_DependencyInversion
 */
public class Demo03_DependencyInversion {

    // ====================================================================
    //  DOMAINE METIER - Le coeur de l'hexagone
    //  Aucune dependance vers l'exterieur.
    // ====================================================================

    /**
     * Objet valeur representant une notification.
     * Record Java 17+ : immutable, equals/hashCode/toString generes.
     */
    record Notification(String recipient, String subject, String message) {}

    /**
     * Resultat d'un envoi de notification.
     * Sealed interface : seuls Success et Failure peuvent l'implementer.
     * Cela remplace les exceptions pour le controle de flux.
     */
    sealed interface SendResult {
        record Success(String details) implements SendResult {}
        record Failure(String reason) implements SendResult {}
    }

    /**
     * PORT SORTANT (Driven Port) - Contrat de notification
     *
     * C'est le DOMAINE qui definit cette interface.
     * Le domaine dit : "J'ai besoin d'envoyer des notifications,
     * mais je ne veux pas savoir comment."
     *
     * Toute implementation concrete (email, SMS, Slack, test)
     * doit respecter CE contrat.
     */
    interface NotificationSender {
        /**
         * Envoyer une notification.
         * @param notification la notification a envoyer
         * @return le resultat de l'envoi
         */
        SendResult send(Notification notification);

        /** Nom du canal pour les logs */
        String channelName();
    }

    // ====================================================================
    //  ADAPTATEURS - Implementations concretes du port
    //  Ils vivent en DEHORS de l'hexagone (couche infrastructure).
    //  Chaque adaptateur implemente le port defini par le domaine.
    // ====================================================================

    /**
     * ADAPTATEUR 1 : Email via SMTP
     * En production, utiliserait JavaMail / Jakarta Mail.
     */
    static class EmailNotificationSender implements NotificationSender {

        private final String smtpServer;

        EmailNotificationSender(String smtpServer) {
            this.smtpServer = smtpServer;
        }

        @Override
        public SendResult send(Notification notification) {
            // Simulation d'envoi email
            System.out.println("    [EMAIL] Connexion a %s...".formatted(smtpServer));
            System.out.println("    [EMAIL] De: noreply@boutique.com");
            System.out.println("    [EMAIL] A: %s".formatted(notification.recipient()));
            System.out.println("    [EMAIL] Sujet: %s".formatted(notification.subject()));
            System.out.println("    [EMAIL] Corps: %s".formatted(notification.message()));
            return new SendResult.Success("Email envoye via " + smtpServer);
        }

        @Override
        public String channelName() { return "Email (SMTP)"; }
    }

    /**
     * ADAPTATEUR 2 : SMS via passerelle
     * En production, utiliserait Twilio, OVH SMS, etc.
     */
    static class SmsNotificationSender implements NotificationSender {

        private final String apiKey;

        SmsNotificationSender(String apiKey) {
            this.apiKey = apiKey;
        }

        @Override
        public SendResult send(Notification notification) {
            // Simulation d'envoi SMS
            if (notification.recipient() == null || notification.recipient().isBlank()) {
                return new SendResult.Failure("Numero de telephone manquant");
            }
            System.out.println("    [SMS] Authentification avec cle API: %s...".formatted(
                    apiKey.substring(0, 4) + "****"));
            System.out.println("    [SMS] Envoi a: %s".formatted(notification.recipient()));
            // Les SMS n'ont pas de sujet, on concatene
            String smsBody = notification.subject() + " - " + notification.message();
            // Troncature a 160 caracteres comme un vrai SMS
            if (smsBody.length() > 160) {
                smsBody = smsBody.substring(0, 157) + "...";
            }
            System.out.println("    [SMS] Message (%d car.): %s".formatted(smsBody.length(), smsBody));
            return new SendResult.Success("SMS envoye a " + notification.recipient());
        }

        @Override
        public String channelName() { return "SMS (Passerelle)"; }
    }

    /**
     * ADAPTATEUR 3 : InMemory - Pour les TESTS unitaires
     *
     * C'est la PUISSANCE du DIP : on peut creer un adaptateur
     * de test qui stocke les notifications en memoire sans
     * aucune infrastructure reelle.
     *
     * En test, on verifie QUOI a ete envoye, pas COMMENT.
     */
    static class InMemoryNotificationSender implements NotificationSender {

        // Stockage de toutes les notifications envoyees
        private final List<Notification> sentNotifications = new ArrayList<>();

        @Override
        public SendResult send(Notification notification) {
            sentNotifications.add(notification);
            System.out.println("    [IN-MEMORY] Notification stockee (total: %d)"
                    .formatted(sentNotifications.size()));
            return new SendResult.Success("Stockee en memoire");
        }

        @Override
        public String channelName() { return "InMemory (Test)"; }

        // --- Methodes utilitaires pour les assertions de test ---

        /** Nombre de notifications envoyees */
        int count() { return sentNotifications.size(); }

        /** Recuperer la derniere notification */
        Notification lastSent() {
            if (sentNotifications.isEmpty()) return null;
            return sentNotifications.get(sentNotifications.size() - 1);
        }

        /** Verifier si un destinataire a ete notifie */
        boolean wasNotified(String recipient) {
            return sentNotifications.stream()
                    .anyMatch(n -> n.recipient().equals(recipient));
        }

        /** Vider les notifications (reset entre tests) */
        void clear() { sentNotifications.clear(); }
    }

    // ====================================================================
    //  USE CASE - Logique metier pure
    //  Ne depend QUE du port NotificationSender.
    //  Ne sait rien de l'email, du SMS, ou du stockage en memoire.
    // ====================================================================

    /**
     * Cas d'utilisation : Passer une commande
     *
     * Ce use case contient la LOGIQUE METIER :
     *   - Validation du montant minimum
     *   - Calcul du total
     *   - Declenchement de la notification
     *
     * Il ne connait PAS le canal de notification.
     * C'est l'assembleur (main) qui decide quel adaptateur utiliser.
     */
    static class OrderUseCase {

        // DEPENDANCE VERS LE PORT (abstraction), pas vers une implementation
        private final NotificationSender notificationSender;
        private static final double MINIMUM_ORDER = 15.0;

        /**
         * Constructeur avec injection du port.
         * Pas besoin de Spring : un simple constructeur suffit.
         */
        OrderUseCase(NotificationSender notificationSender) {
            this.notificationSender = notificationSender;
        }

        /**
         * Record pour les articles de commande
         */
        record Item(String name, int quantity, double price) {
            double total() { return quantity * price; }
        }

        /**
         * Record pour le resultat de la commande
         */
        record OrderResult(String orderId, double total, SendResult notificationResult) {}

        /**
         * Passer une commande - Logique metier pure
         */
        OrderResult placeOrder(String customerContact, List<Item> items) {
            // 1. Calcul du total (logique metier)
            double total = items.stream().mapToDouble(Item::total).sum();

            // 2. Validation (regle metier)
            if (items.isEmpty()) {
                throw new IllegalArgumentException("La commande doit contenir au moins un article");
            }
            if (total < MINIMUM_ORDER) {
                throw new IllegalArgumentException(
                        "Montant minimum : %.2f EUR (actuel : %.2f EUR)".formatted(MINIMUM_ORDER, total));
            }

            // 3. Generation d'un identifiant (simplifie)
            String orderId = "ORD-" + System.nanoTime() % 100000;

            // 4. Notification via le PORT - on ne sait pas quel canal sera utilise
            var notification = new Notification(
                    customerContact,
                    "Confirmation de commande " + orderId,
                    "Votre commande de %.2f EUR (%d article(s)) a ete confirmee."
                            .formatted(total, items.size())
            );
            var sendResult = notificationSender.send(notification);

            return new OrderResult(orderId, total, sendResult);
        }
    }

    // ====================================================================
    //  ASSEMBLAGE MANUEL (sans framework)
    //  C'est ici qu'on branche les adaptateurs sur les ports.
    //  En production, Spring ferait ce travail avec @Autowired.
    //  Mais cette demo prouve que le DIP fonctionne SANS Spring.
    // ====================================================================

    public static void main(String[] args) {
        System.out.println("=".repeat(70));
        System.out.println("DEMO 03 : Dependency Inversion Principle (DIP) en pratique");
        System.out.println("=".repeat(70));
        System.out.println();

        var items = List.of(
                new OrderUseCase.Item("Architecture Hexagonale", 1, 42.00),
                new OrderUseCase.Item("Clean Code", 2, 35.00)
        );

        // =================================================================
        // SCENARIO 1 : Assemblage avec l'adaptateur Email
        // En production, on utiliserait cette configuration.
        // =================================================================
        System.out.println(">>> SCENARIO 1 : Notification par Email");
        System.out.println("-".repeat(50));
        System.out.println("  Assemblage : OrderUseCase <-- NotificationSender <|-- EmailNotificationSender");
        System.out.println();

        // Assemblage manuel : on "branche" l'adaptateur email sur le port
        NotificationSender emailSender = new EmailNotificationSender("smtp.boutique.com");
        OrderUseCase useCase1 = new OrderUseCase(emailSender);

        var result1 = useCase1.placeOrder("client@example.com", items);
        System.out.println();
        System.out.println("  Resultat : %s | Total: %.2f EUR | Notification: %s"
                .formatted(result1.orderId(), result1.total(), result1.notificationResult()));
        System.out.println();

        // =================================================================
        // SCENARIO 2 : Assemblage avec l'adaptateur SMS
        // Meme use case, meme logique, canal different.
        // ON N'A PAS MODIFIE UNE SEULE LIGNE de OrderUseCase !
        // =================================================================
        System.out.println(">>> SCENARIO 2 : Notification par SMS");
        System.out.println("-".repeat(50));
        System.out.println("  Assemblage : OrderUseCase <-- NotificationSender <|-- SmsNotificationSender");
        System.out.println();

        // Assemblage manuel : on "branche" l'adaptateur SMS
        NotificationSender smsSender = new SmsNotificationSender("sk-7f3a9b2c");
        OrderUseCase useCase2 = new OrderUseCase(smsSender);

        var result2 = useCase2.placeOrder("+33612345678", items);
        System.out.println();
        System.out.println("  Resultat : %s | Total: %.2f EUR | Notification: %s"
                .formatted(result2.orderId(), result2.total(), result2.notificationResult()));
        System.out.println();

        // =================================================================
        // SCENARIO 3 : Assemblage avec l'adaptateur InMemory (test)
        // On simule ce qu'on ferait dans un test unitaire JUnit.
        // =================================================================
        System.out.println(">>> SCENARIO 3 : Test unitaire avec adaptateur InMemory");
        System.out.println("-".repeat(50));
        System.out.println("  Assemblage : OrderUseCase <-- NotificationSender <|-- InMemoryNotificationSender");
        System.out.println();

        // Assemblage pour le test
        var inMemorySender = new InMemoryNotificationSender();
        OrderUseCase useCaseTest = new OrderUseCase(inMemorySender);

        // Execution
        var result3 = useCaseTest.placeOrder("test@test.com", items);

        // Assertions (comme dans JUnit)
        System.out.println();
        System.out.println("  --- Assertions de test ---");
        assert inMemorySender.count() == 1 : "Devrait avoir 1 notification";
        System.out.println("  [PASS] 1 notification envoyee");

        assert inMemorySender.wasNotified("test@test.com") : "test@test.com devrait etre notifie";
        System.out.println("  [PASS] test@test.com a ete notifie");

        var lastNotif = inMemorySender.lastSent();
        assert lastNotif.subject().contains("Confirmation") : "Le sujet devrait contenir 'Confirmation'";
        System.out.println("  [PASS] Le sujet contient 'Confirmation'");

        assert result3.total() == 112.0 : "Le total devrait etre 112.00 EUR";
        System.out.println("  [PASS] Total = %.2f EUR".formatted(result3.total()));

        System.out.println();
        System.out.println("  Tous les tests passent SANS infrastructure reelle !");
        System.out.println();

        // =================================================================
        // SCENARIO 4 : Validation de la regle metier
        // Le comportement est identique quel que soit l'adaptateur.
        // =================================================================
        System.out.println(">>> SCENARIO 4 : Validation metier (commande trop petite)");
        System.out.println("-".repeat(50));
        try {
            useCaseTest.placeOrder("test@test.com", List.of(
                    new OrderUseCase.Item("Marque-page", 1, 2.50)
            ));
            System.out.println("  [FAIL] Aurait du lever une exception !");
        } catch (IllegalArgumentException e) {
            System.out.println("  [PASS] Exception levee : " + e.getMessage());
        }
        System.out.println();

        // =================================================================
        //  BILAN
        // =================================================================
        System.out.println("=".repeat(70));
        System.out.println("BILAN - Dependency Inversion Principle");
        System.out.println("-".repeat(70));
        System.out.println();
        System.out.println("  Ce qui a ete demontre :");
        System.out.println("  1. Le PORT (NotificationSender) est defini par le DOMAINE");
        System.out.println("  2. Les ADAPTATEURS (Email, SMS, InMemory) implementent le port");
        System.out.println("  3. Le USE CASE ne connait QUE le port, jamais les adaptateurs");
        System.out.println("  4. L'assemblage se fait dans le main() - pas besoin de Spring");
        System.out.println("  5. On teste avec un adaptateur InMemory - pas besoin de SMTP");
        System.out.println();
        System.out.println("  Principe cle :");
        System.out.println("    SANS DIP : UseCase --> EmailSender (couplage fort)");
        System.out.println("    AVEC DIP : UseCase --> Port <|-- EmailSender (couplage faible)");
        System.out.println();
        System.out.println("  En architecture hexagonale, TOUS les ports suivent ce principe.");
        System.out.println("  Le domaine est isole et protege de tout changement externe.");
        System.out.println("=".repeat(70));
    }
}
