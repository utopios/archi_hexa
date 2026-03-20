package com.utopios.module3.domain;

import com.utopios.module3.domain.exception.OrderNotModifiableException;
import com.utopios.module3.domain.model.CustomerId;
import com.utopios.module3.domain.model.Email;
import com.utopios.module3.domain.model.Money;
import com.utopios.module3.domain.model.Order;
import com.utopios.module3.domain.model.OrderId;
import com.utopios.module3.domain.model.OrderItem;
import com.utopios.module3.domain.model.ProductId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitaires de l'agrégat Order.
 * Niveau 1 de la pyramide : domaine pur, aucune annotation Spring, ultra-rapides.
 */
@DisplayName("Order - Agrégat racine")
class OrderTest {

    private static final CustomerId CUSTOMER_ID = CustomerId.of("CLIENT-001");
    private static final Email CUSTOMER_EMAIL = Email.of("client@test.com");

    // --- Création ---

    @Test
    @DisplayName("testNouvelleCommandeEnDraft : une nouvelle commande est en statut DRAFT")
    void testNouvelleCommandeEnDraft() {
        Order order = Order.create(CUSTOMER_ID, CUSTOMER_EMAIL);

        assertThat(order.getStatus()).isEqualTo(Order.Status.DRAFT);
        assertThat(order.getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(order.getItems()).isEmpty();
        assertThat(order.getOrderId()).isNotNull();
        assertThat(order.getOrderId().value()).isNotBlank();
        assertThat(order.getDiscountPct()).isEqualTo(0.0);
    }

    // --- Ajout d'articles ---

    @Test
    @DisplayName("testAjoutArticle : on peut ajouter un article à une commande DRAFT")
    void testAjoutArticle() {
        Order order = Order.create(CUSTOMER_ID, CUSTOMER_EMAIL);
        OrderItem item = clavier();

        order.addItem(item);

        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).name()).isEqualTo("Clavier");
    }

    // --- Calcul du total ---

    @Test
    @DisplayName("testCalculTotal : total = somme des sous-totaux sans réduction")
    void testCalculTotal() {
        Order order = Order.create(CUSTOMER_ID, CUSTOMER_EMAIL);
        order.addItem(clavier());    // 89.99 × 1 = 89.99
        order.addItem(souris());     // 49.99 × 2 = 99.98

        double total = order.calculateTotal();

        // 89.99 + 99.98 = 189.97
        assertThat(total).isCloseTo(189.97, within(0.01));
    }

    @Test
    @DisplayName("testCalculTotalAvecReduction : total réduit de 10% = 171.973")
    void testCalculTotalAvecReduction() {
        Order order = Order.create(CUSTOMER_ID, CUSTOMER_EMAIL);
        order.addItem(ecran200Eur());  // 200 EUR

        order.applyDiscount(10.0);

        double total = order.calculateTotal();

        assertThat(total).isCloseTo(180.0, within(0.01));
    }

    // --- Confirmation ---

    @Test
    @DisplayName("testConfirmationReussie : une commande avec articles passe à CONFIRMED")
    void testConfirmationReussie() {
        Order order = Order.create(CUSTOMER_ID, CUSTOMER_EMAIL);
        order.addItem(clavier());

        order.confirm();

        assertThat(order.getStatus()).isEqualTo(Order.Status.CONFIRMED);
    }

    @Test
    @DisplayName("testConfirmationCommandeVideInterdite : confirmer une commande vide lève une exception")
    void testConfirmationCommandeVideInterdite() {
        Order order = Order.create(CUSTOMER_ID, CUSTOMER_EMAIL);

        assertThatThrownBy(order::confirm)
                .isInstanceOf(OrderNotModifiableException.class)
                .hasMessageContaining("aucun article");
    }

    @Test
    @DisplayName("testConfirmationCommandeDejaConfirmeeInterdite : confirmer deux fois lève une exception")
    void testConfirmationCommandeDejaConfirmeeInterdite() {
        Order order = Order.create(CUSTOMER_ID, CUSTOMER_EMAIL);
        order.addItem(clavier());
        order.confirm();

        assertThatThrownBy(order::confirm)
                .isInstanceOf(OrderNotModifiableException.class)
                .hasMessageContaining("DRAFT");
    }

    // --- Interdiction d'ajout après confirmation ---

    @Test
    @DisplayName("testAjoutArticleApresConfirmationInterdit : ajouter un article après confirmation lève une exception")
    void testAjoutArticleApresConfirmationInterdit() {
        Order order = Order.create(CUSTOMER_ID, CUSTOMER_EMAIL);
        order.addItem(clavier());
        order.confirm();

        assertThatThrownBy(() -> order.addItem(souris()))
                .isInstanceOf(OrderNotModifiableException.class)
                .hasMessageContaining("DRAFT");
    }

    // --- Limites de réduction ---

    @Test
    @DisplayName("testReduction0Et50InterditsLimites : 0% et 50% sont refusés (bornes exclues)")
    void testReduction0Et50InterditsLimites() {
        Order order = Order.create(CUSTOMER_ID, CUSTOMER_EMAIL);
        order.addItem(clavier());

        // 0% interdit
        assertThatThrownBy(() -> order.applyDiscount(0.0))
                .isInstanceOf(IllegalArgumentException.class);

        // 50% interdit
        assertThatThrownBy(() -> order.applyDiscount(50.0))
                .isInstanceOf(IllegalArgumentException.class);

        // 49.9% autorisé
        assertThatCode(() -> order.applyDiscount(49.9))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("testReductionSurCommandeVideInterdite : appliquer une réduction sur commande vide lève une exception")
    void testReductionSurCommandeVideInterdite() {
        Order order = Order.create(CUSTOMER_ID, CUSTOMER_EMAIL);

        assertThatThrownBy(() -> order.applyDiscount(10.0))
                .isInstanceOf(OrderNotModifiableException.class)
                .hasMessageContaining("vide");
    }

    // --- Annulation ---

    @Test
    @DisplayName("testAnnulationCommandeConfirmee : une commande CONFIRMED peut être annulée")
    void testAnnulationCommandeConfirmee() {
        Order order = Order.create(CUSTOMER_ID, CUSTOMER_EMAIL);
        order.addItem(clavier());
        order.confirm();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(Order.Status.CANCELLED);
    }

    @Test
    @DisplayName("testAnnulationCommandeExpedieeInterdite : une commande SHIPPED ne peut pas être annulée")
    void testAnnulationCommandeExpedieeInterdite() {
        // Reconstitution d'une commande expédiée
        Order order = Order.reconstitute(
                OrderId.of("ORDER-001"),
                CUSTOMER_ID,
                CUSTOMER_EMAIL,
                java.util.List.of(clavier()),
                Order.Status.SHIPPED,
                0.0
        );

        assertThatThrownBy(order::cancel)
                .isInstanceOf(OrderNotModifiableException.class)
                .hasMessageContaining("expédiée");
    }

    // --- Helpers ---

    private OrderItem clavier() {
        return OrderItem.of(ProductId.of("PROD-001"), "Clavier", Money.of(89.99, "EUR"), 1);
    }

    private OrderItem souris() {
        return OrderItem.of(ProductId.of("PROD-002"), "Souris", Money.of(49.99, "EUR"), 2);
    }

    private OrderItem ecran200Eur() {
        return OrderItem.of(ProductId.of("PROD-003"), "Ecran", Money.of(200.0, "EUR"), 1);
    }
}
