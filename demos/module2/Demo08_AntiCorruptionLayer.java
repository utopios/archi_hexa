package com.utopios.hexagonal.demos.module2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Demo 08 - Anti-Corruption Layer (ACL)
 *
 * Illustre le pattern Anti-Corruption Layer :
 * - Un système legacy externe avec son propre modèle (noms de champs incohérents, formats bizarres)
 * - Notre modèle domaine propre avec des concepts métier clairs
 * - L'ACL (adaptateur) traduit entre les deux mondes
 * - Le domaine n'est JAMAIS pollué par le modèle legacy
 *
 * Cas d'usage : intégration d'un système de paiement legacy
 * qui utilise des noms de champs cryptiques et des formats non standards.
 */
public class Demo08_AntiCorruptionLayer {

    // =========================================================================
    // SYSTÈME LEGACY EXTERNE - Modèle que nous ne contrôlons pas
    // =========================================================================

    /**
     * Requête du système de paiement legacy.
     * Noms de champs cryptiques, formats non standards.
     * On ne peut PAS modifier cette classe (système externe).
     */
    public record LegacyPaymentRequest(
            String txn_ref_id,          // Identifiant de transaction (format: TXN-YYYYMMDD-XXXX)
            String merch_cd,            // Code commerçant
            double amt_val,             // Montant en centimes (!) pas en euros
            String ccy_cd,              // Code devise (format numérique ISO : "978" pour EUR)
            String crd_num_masked,      // Numéro de carte masqué
            String crd_hldr_nm,         // Nom du titulaire de la carte
            String txn_dt_tm,           // Date au format "YYYYMMDDHHmmss"
            int txn_typ,                // Type : 1=Autorisation, 2=Capture, 3=Remboursement
            String rtrn_url,            // URL de callback
            Map<String, String> xtra    // Champs supplémentaires arbitraires
    ) {}

    /**
     * Réponse du système de paiement legacy.
     */
    public record LegacyPaymentResponse(
            String txn_ref_id,
            int rsp_cd,                 // Code réponse : 0=OK, 1=Refusé, 2=Erreur, 9=Timeout
            String rsp_msg,             // Message cryptique du legacy
            String auth_cd,             // Code d'autorisation
            String proc_dt_tm           // Date de traitement
    ) {}

    /**
     * Client du système de paiement legacy (simulé).
     * Représente l'API externe que nous appelons.
     */
    public static class LegacyPaymentSystemClient {

        /**
         * Simule un appel au système de paiement legacy.
         */
        public LegacyPaymentResponse processPayment(LegacyPaymentRequest request) {
            System.out.println("    [LEGACY] Traitement de la requête legacy...");
            System.out.println("    [LEGACY] txn_ref_id=" + request.txn_ref_id());
            System.out.println("    [LEGACY] amt_val=" + request.amt_val() + " (centimes)");
            System.out.println("    [LEGACY] ccy_cd=" + request.ccy_cd());
            System.out.println("    [LEGACY] txn_typ=" + request.txn_typ());

            // Simulation : paiement accepté si montant < 100000 centimes (1000€)
            boolean accepted = request.amt_val() < 100000;

            String now = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

            if (accepted) {
                return new LegacyPaymentResponse(
                        request.txn_ref_id(),
                        0,                                  // 0 = succès
                        "TXN_APPROVED_00",                  // message cryptique
                        "AUTH" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                        now
                );
            } else {
                return new LegacyPaymentResponse(
                        request.txn_ref_id(),
                        1,                                  // 1 = refusé
                        "TXN_DECLINED_LIMIT_EXCEEDED_51",   // message cryptique
                        null,
                        now
                );
            }
        }
    }

    // =========================================================================
    // MODÈLE DU DOMAINE - Notre modèle propre et expressif
    // =========================================================================

    /**
     * Enum des devises du domaine, avec correspondance ISO numérique.
     */
    public enum DomainCurrency {
        EUR("978", "€"),
        USD("840", "$"),
        GBP("826", "£");

        private final String isoNumeric;
        private final String symbol;

        DomainCurrency(String isoNumeric, String symbol) {
            this.isoNumeric = isoNumeric;
            this.symbol = symbol;
        }

        public String getIsoNumeric() { return isoNumeric; }
        public String getSymbol() { return symbol; }

        /**
         * Trouve une devise par son code ISO numérique.
         */
        public static DomainCurrency fromIsoNumeric(String code) {
            for (DomainCurrency c : values()) {
                if (c.isoNumeric.equals(code)) return c;
            }
            throw new IllegalArgumentException("Code devise ISO inconnu : " + code);
        }
    }

    /**
     * Value Object représentant un montant dans notre domaine (en euros, pas en centimes).
     */
    public record Amount(BigDecimal value, DomainCurrency currency) {

        public Amount {
            Objects.requireNonNull(value, "Le montant est requis");
            Objects.requireNonNull(currency, "La devise est requise");
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Le montant ne peut pas être négatif");
            }
            value = value.setScale(2, RoundingMode.HALF_UP);
        }

        /**
         * Convertit le montant en centimes (pour le système legacy).
         */
        public long toCents() {
            return value.multiply(BigDecimal.valueOf(100)).longValue();
        }

        /**
         * Crée un Amount à partir d'un montant en centimes.
         */
        public static Amount fromCents(long cents, DomainCurrency currency) {
            return new Amount(
                    BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP),
                    currency
            );
        }

        @Override
        public String toString() {
            return value + " " + currency.getSymbol();
        }
    }

    /**
     * Types de transaction dans notre domaine.
     */
    public enum TransactionType {
        AUTHORIZATION,
        CAPTURE,
        REFUND
    }

    /**
     * Résultat de paiement dans notre domaine.
     */
    public enum PaymentStatus {
        SUCCESS,
        DECLINED,
        ERROR,
        TIMEOUT
    }

    /**
     * Entité Payment de notre domaine.
     * Modèle propre, expressif, avec des noms de champs clairs.
     */
    public static class Payment {

        private final String paymentId;
        private final String orderId;
        private final Amount amount;
        private final String cardHolderName;
        private final String maskedCardNumber;
        private final TransactionType transactionType;
        private final Instant requestedAt;

        private PaymentStatus status;
        private String authorizationCode;
        private Instant processedAt;
        private String failureReason;

        public Payment(String paymentId, String orderId, Amount amount,
                       String cardHolderName, String maskedCardNumber,
                       TransactionType transactionType) {
            this.paymentId = Objects.requireNonNull(paymentId);
            this.orderId = Objects.requireNonNull(orderId);
            this.amount = Objects.requireNonNull(amount);
            this.cardHolderName = Objects.requireNonNull(cardHolderName);
            this.maskedCardNumber = Objects.requireNonNull(maskedCardNumber);
            this.transactionType = Objects.requireNonNull(transactionType);
            this.requestedAt = Instant.now();
            this.status = null; // pas encore traité
        }

        /**
         * Marque le paiement comme réussi.
         */
        public void markAsSuccessful(String authorizationCode, Instant processedAt) {
            this.status = PaymentStatus.SUCCESS;
            this.authorizationCode = authorizationCode;
            this.processedAt = processedAt;
        }

        /**
         * Marque le paiement comme refusé.
         */
        public void markAsDeclined(String reason, Instant processedAt) {
            this.status = PaymentStatus.DECLINED;
            this.failureReason = reason;
            this.processedAt = processedAt;
        }

        /**
         * Marque le paiement comme en erreur.
         */
        public void markAsError(String reason) {
            this.status = PaymentStatus.ERROR;
            this.failureReason = reason;
            this.processedAt = Instant.now();
        }

        public boolean isSuccessful() {
            return status == PaymentStatus.SUCCESS;
        }

        // Accesseurs
        public String getPaymentId() { return paymentId; }
        public String getOrderId() { return orderId; }
        public Amount getAmount() { return amount; }
        public String getCardHolderName() { return cardHolderName; }
        public String getMaskedCardNumber() { return maskedCardNumber; }
        public TransactionType getTransactionType() { return transactionType; }
        public PaymentStatus getStatus() { return status; }
        public String getAuthorizationCode() { return authorizationCode; }
        public Instant getRequestedAt() { return requestedAt; }
        public Instant getProcessedAt() { return processedAt; }
        public String getFailureReason() { return failureReason; }

        @Override
        public String toString() {
            return String.format("Payment[id=%s, commande=%s, montant=%s, type=%s, statut=%s%s]",
                    paymentId, orderId, amount, transactionType, status,
                    authorizationCode != null ? ", auth=" + authorizationCode : "");
        }
    }

    // =========================================================================
    // PORT SECONDAIRE - Défini dans le domaine
    // =========================================================================

    /**
     * Port secondaire : interface de passerelle de paiement.
     * Défini dans le domaine, en termes métier.
     * Aucune notion du système legacy ici.
     */
    public interface PaymentGateway {

        /**
         * Traite un paiement et met à jour son statut.
         * @param payment le paiement à traiter (sera modifié avec le résultat)
         */
        void processPayment(Payment payment);
    }

    // =========================================================================
    // ANTI-CORRUPTION LAYER - L'adaptateur qui protège notre domaine
    // =========================================================================

    /**
     * Anti-Corruption Layer : adaptateur qui traduit entre notre domaine
     * et le système de paiement legacy.
     *
     * Responsabilités de l'ACL :
     * 1. Traduire notre modèle domaine → modèle legacy (requête sortante)
     * 2. Appeler le système legacy
     * 3. Traduire la réponse legacy → notre modèle domaine (réponse entrante)
     * 4. Gérer les erreurs et les cas limites du legacy
     *
     * Le domaine n'est JAMAIS exposé au modèle legacy grâce à cette couche.
     */
    public static class LegacyPaymentAdapter implements PaymentGateway {

        private static final String MERCHANT_CODE = "MERCH_UTOPIOS_001";
        private static final String CALLBACK_URL = "https://api.utopios.com/payments/callback";

        private static final DateTimeFormatter LEGACY_DATE_FORMAT =
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

        private final LegacyPaymentSystemClient legacyClient;

        public LegacyPaymentAdapter(LegacyPaymentSystemClient legacyClient) {
            this.legacyClient = legacyClient;
        }

        @Override
        public void processPayment(Payment payment) {
            System.out.println("  [ACL] Traduction domaine → legacy...");

            try {
                // Étape 1 : Traduire le modèle domaine en requête legacy
                LegacyPaymentRequest legacyRequest = translateToLegacy(payment);
                System.out.println("  [ACL] Requête legacy construite");

                // Étape 2 : Appeler le système legacy
                LegacyPaymentResponse legacyResponse = legacyClient.processPayment(legacyRequest);

                // Étape 3 : Traduire la réponse legacy en résultat domaine
                System.out.println("  [ACL] Traduction réponse legacy → domaine...");
                translateFromLegacy(legacyResponse, payment);

            } catch (Exception e) {
                // Étape 4 : Gérer les erreurs du legacy
                System.out.println("  [ACL] Erreur lors de l'appel legacy : " + e.getMessage());
                payment.markAsError("Erreur de communication avec le système de paiement : "
                        + e.getMessage());
            }
        }

        // --- Méthodes de traduction (le coeur de l'ACL) ---

        /**
         * Traduit un Payment du domaine en LegacyPaymentRequest.
         * C'est ici que l'on gère toutes les bizarreries du legacy.
         */
        private LegacyPaymentRequest translateToLegacy(Payment payment) {
            // Générer le txn_ref_id au format legacy : TXN-YYYYMMDD-XXXX
            String txnRefId = String.format("TXN-%s-%s",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                    payment.getPaymentId().substring(0, Math.min(4, payment.getPaymentId().length())));

            // Convertir le montant : euros → centimes (le legacy travaille en centimes !)
            double amountInCents = payment.getAmount().toCents();

            // Convertir la devise : code alphabétique → code numérique ISO
            String isoCurrencyCode = payment.getAmount().currency().getIsoNumeric();

            // Convertir le type de transaction : enum → int cryptique
            int txnType = translateTransactionType(payment.getTransactionType());

            // Date au format legacy
            String dateTime = LocalDateTime.now().format(LEGACY_DATE_FORMAT);

            // Champs supplémentaires requis par le legacy
            Map<String, String> extra = new HashMap<>();
            extra.put("order_ref", payment.getOrderId());
            extra.put("channel", "WEB");
            extra.put("ip_addr", "0.0.0.0");

            return new LegacyPaymentRequest(
                    txnRefId,
                    MERCHANT_CODE,
                    amountInCents,
                    isoCurrencyCode,
                    payment.getMaskedCardNumber(),
                    payment.getCardHolderName(),
                    dateTime,
                    txnType,
                    CALLBACK_URL,
                    extra
            );
        }

        /**
         * Traduit la réponse legacy en mise à jour du Payment domaine.
         */
        private void translateFromLegacy(LegacyPaymentResponse response, Payment payment) {
            // Convertir la date de traitement legacy → Instant
            Instant processedAt = parseProcessedDate(response.proc_dt_tm());

            // Convertir le code réponse legacy → statut domaine
            PaymentStatus domainStatus = translateResponseCode(response.rsp_cd());

            switch (domainStatus) {
                case SUCCESS -> {
                    payment.markAsSuccessful(response.auth_cd(), processedAt);
                    System.out.println("  [ACL] Paiement réussi, code auth : " + response.auth_cd());
                }
                case DECLINED -> {
                    String reason = translateDeclineReason(response.rsp_msg());
                    payment.markAsDeclined(reason, processedAt);
                    System.out.println("  [ACL] Paiement refusé : " + reason);
                }
                case ERROR, TIMEOUT -> {
                    payment.markAsError(translateErrorMessage(response.rsp_msg()));
                    System.out.println("  [ACL] Erreur paiement : " + response.rsp_msg());
                }
            }
        }

        /**
         * Convertit un TransactionType domaine en code entier legacy.
         */
        private int translateTransactionType(TransactionType type) {
            return switch (type) {
                case AUTHORIZATION -> 1;
                case CAPTURE -> 2;
                case REFUND -> 3;
            };
        }

        /**
         * Convertit un code réponse legacy en PaymentStatus domaine.
         */
        private PaymentStatus translateResponseCode(int responseCode) {
            return switch (responseCode) {
                case 0 -> PaymentStatus.SUCCESS;
                case 1 -> PaymentStatus.DECLINED;
                case 9 -> PaymentStatus.TIMEOUT;
                default -> PaymentStatus.ERROR;
            };
        }

        /**
         * Traduit le message cryptique de refus legacy en message humain.
         */
        private String translateDeclineReason(String legacyMessage) {
            if (legacyMessage == null) return "Raison inconnue";

            // Mapper les codes cryptiques du legacy en messages lisibles
            if (legacyMessage.contains("LIMIT_EXCEEDED")) {
                return "Plafond de paiement dépassé";
            } else if (legacyMessage.contains("INSUFFICIENT_FUNDS")) {
                return "Fonds insuffisants";
            } else if (legacyMessage.contains("CARD_EXPIRED")) {
                return "Carte expirée";
            } else if (legacyMessage.contains("FRAUD_SUSPECTED")) {
                return "Suspicion de fraude - paiement bloqué";
            } else {
                return "Paiement refusé (code: " + legacyMessage + ")";
            }
        }

        /**
         * Traduit un message d'erreur legacy en message compréhensible.
         */
        private String translateErrorMessage(String legacyMessage) {
            if (legacyMessage == null) return "Erreur inconnue du système de paiement";
            return "Erreur système de paiement : " + legacyMessage;
        }

        /**
         * Parse la date au format legacy "YYYYMMDDHHmmss" en Instant.
         */
        private Instant parseProcessedDate(String legacyDateTime) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(legacyDateTime, LEGACY_DATE_FORMAT);
                return ldt.atZone(ZoneId.systemDefault()).toInstant();
            } catch (Exception e) {
                return Instant.now(); // fallback si le format est incorrect
            }
        }
    }

    // =========================================================================
    // DÉMONSTRATION
    // =========================================================================

    public static void main(String[] args) {
        System.out.println("=== Demo 08 : Anti-Corruption Layer (ACL) ===\n");

        System.out.println("Architecture :");
        System.out.println("  ┌─────────────────────────────────────────────────────────┐");
        System.out.println("  │  DOMAINE (notre monde propre)                           │");
        System.out.println("  │  Payment, Amount, PaymentGateway (port)                 │");
        System.out.println("  └────────────────────────┬────────────────────────────────┘");
        System.out.println("                           │ implémente");
        System.out.println("  ┌────────────────────────▼────────────────────────────────┐");
        System.out.println("  │  ACL (LegacyPaymentAdapter)                             │");
        System.out.println("  │  Traduit : Payment ↔ LegacyPaymentRequest/Response      │");
        System.out.println("  └────────────────────────┬────────────────────────────────┘");
        System.out.println("                           │ appelle");
        System.out.println("  ┌────────────────────────▼────────────────────────────────┐");
        System.out.println("  │  SYSTÈME LEGACY (monde extérieur)                       │");
        System.out.println("  │  txn_ref_id, amt_val (centimes), ccy_cd, txn_typ...     │");
        System.out.println("  └─────────────────────────────────────────────────────────┘");
        System.out.println();

        // --- Configuration ---
        // Le domaine ne connaît que le port (PaymentGateway)
        LegacyPaymentSystemClient legacyClient = new LegacyPaymentSystemClient();
        PaymentGateway paymentGateway = new LegacyPaymentAdapter(legacyClient);

        // =================================================================
        // Scénario 1 : Paiement réussi (montant < 1000€)
        // =================================================================
        System.out.println("--- Scénario 1 : Paiement réussi ---\n");

        // Le domaine crée un Payment avec son propre modèle (propre et expressif)
        Payment payment1 = new Payment(
                "PAY-001",
                "CMD-2024-042",
                new Amount(new BigDecimal("149.99"), DomainCurrency.EUR),
                "Jean Dupont",
                "****-****-****-1234",
                TransactionType.AUTHORIZATION
        );

        System.out.println("Payment domaine créé : " + payment1);
        System.out.println("  Le domaine ne voit QUE son propre modèle.");
        System.out.println("  Il ne sait rien de txn_ref_id, amt_val, ccy_cd...\n");

        // L'ACL traduit et appelle le legacy de manière transparente
        paymentGateway.processPayment(payment1);

        System.out.println("\nRésultat dans le domaine : " + payment1);
        System.out.println("  Statut : " + payment1.getStatus());
        System.out.println("  Code autorisation : " + payment1.getAuthorizationCode());

        // =================================================================
        // Scénario 2 : Paiement refusé (montant >= 1000€)
        // =================================================================
        System.out.println("\n--- Scénario 2 : Paiement refusé (plafond dépassé) ---\n");

        Payment payment2 = new Payment(
                "PAY-002",
                "CMD-2024-043",
                new Amount(new BigDecimal("1500.00"), DomainCurrency.EUR), // > 1000€
                "Marie Martin",
                "****-****-****-5678",
                TransactionType.CAPTURE
        );

        System.out.println("Payment domaine créé : " + payment2 + "\n");

        paymentGateway.processPayment(payment2);

        System.out.println("\nRésultat dans le domaine : " + payment2);
        System.out.println("  Statut : " + payment2.getStatus());
        System.out.println("  Raison (traduite en français) : " + payment2.getFailureReason());
        System.out.println("  → Le message legacy cryptique a été traduit par l'ACL !");

        // =================================================================
        // Scénario 3 : Ce que le domaine ne voit PAS
        // =================================================================
        System.out.println("\n--- Scénario 3 : Isolation du domaine ---\n");

        System.out.println("Ce que le domaine manipule :");
        System.out.println("  - Payment avec paymentId, orderId, Amount en euros");
        System.out.println("  - TransactionType.AUTHORIZATION (enum clair)");
        System.out.println("  - PaymentStatus.SUCCESS / DECLINED (enum clair)");
        System.out.println("  - Des messages en français compréhensibles");
        System.out.println();
        System.out.println("Ce que le domaine ne voit JAMAIS :");
        System.out.println("  - txn_ref_id, merch_cd, amt_val (centimes !)");
        System.out.println("  - ccy_cd='978' au lieu de EUR");
        System.out.println("  - txn_typ=1 au lieu de AUTHORIZATION");
        System.out.println("  - rsp_cd=0 au lieu de SUCCESS");
        System.out.println("  - 'TXN_DECLINED_LIMIT_EXCEEDED_51' au lieu d'un message clair");
        System.out.println();
        System.out.println("L'ACL protège notre domaine de la corruption du modèle legacy !");

        System.out.println("\n=== Points clés ===");
        System.out.println("1. L'ACL est un ADAPTATEUR qui implémente un PORT du domaine");
        System.out.println("2. Il traduit dans les DEUX sens : requête (sortie) et réponse (entrée)");
        System.out.println("3. Le domaine reste pur : noms clairs, types forts, validation");
        System.out.println("4. Les bizarreries du legacy sont confinées dans l'adaptateur");
        System.out.println("5. Si le legacy change, seul l'ACL doit être modifié");
        System.out.println("6. On peut tester le domaine SANS le système legacy");

        System.out.println("\n=== Fin de la démo ===");
    }
}
