package com.utopios.module3.cucumber;

import com.utopios.module3.domain.model.*;
import com.utopios.module3.domain.service.CreateOrderCommand;
import com.utopios.module3.domain.service.OrderItemCommand;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Step Definitions — bridge between Gherkin and Java code.
 *
 * Role in hexagonal architecture:
 * These methods act exactly like an INBOUND ADAPTER.
 * They translate natural language (Gherkin) into calls
 * on the input ports (CreateOrderUseCase, ConfirmOrderUseCase).
 *
 * Compare with OrderRestController which translates HTTP → port.
 * Here: Gherkin → port. Same principle, different "driver".
 *
 * No Spring annotation — services are wired via TestContext.
 */
public class CommandeStepDefinitions {

    private final TestContext ctx = new TestContext();

    @Before
    public void reset() {
        ctx.orderRepository.clear();
        ctx.notificationAdapter.clear();
        ctx.currentOrderId = null;
        ctx.currentCustomerId = null;
        ctx.currentCustomerEmail = null;
        ctx.lastResult = null;
        ctx.capturedException = null;
    }

    // =========================================================================
    // GIVEN — Preconditions
    // =========================================================================

    @Given("the system is initialized with in-memory adapters")
    public void systemInitialized() {
        // Nothing to do — TestContext already creates in-memory adapters
        // This step explicitly documents the test architecture
    }

    @Given("a customer {string} with email {string}")
    public void aCustomerWithEmail(String customerId, String email) {
        ctx.currentCustomerId = customerId;
        ctx.currentCustomerEmail = email;
    }

    @Given("an existing DRAFT order for customer {string}")
    public void anExistingDraftOrder(String customerId) {
        Order order = Order.create(CustomerId.of(customerId), Email.of(customerId + "@test.com"));
        ctx.orderRepository.save(order);
        ctx.currentOrderId = order.getOrderId().value();
    }

    @And("the order contains an item {string} at {double} euros")
    public void theOrderContainsAnItem(String name, double price) {
        Order order = ctx.orderRepository.findById(OrderId.of(ctx.currentOrderId))
                .orElseThrow(() -> new AssertionError("Order not found: " + ctx.currentOrderId));
        order.addItem(OrderItem.of(ProductId.of("PROD-AUTO"), name, Money.of(price, "EUR"), 1));
        ctx.orderRepository.save(order);
    }

    @Given("an empty DRAFT order for customer {string}")
    public void anEmptyDraftOrder(String customerId) {
        Order order = Order.create(CustomerId.of(customerId), Email.of(customerId + "@test.com"));
        ctx.orderRepository.save(order);
        ctx.currentOrderId = order.getOrderId().value();
    }

    @Given("an order with item {string} at {double} euros for customer {string}")
    public void anOrderWithItem(String name, double price, String customerId) {
        Order order = Order.create(CustomerId.of(customerId), Email.of(customerId + "@test.com"));
        order.addItem(OrderItem.of(ProductId.of("PROD-AUTO"), name, Money.of(price, "EUR"), 1));
        ctx.orderRepository.save(order);
        ctx.currentOrderId = order.getOrderId().value();
    }

    // =========================================================================
    // WHEN — Actions
    // =========================================================================

    @When("they create an order with the following items:")
    public void theyCreateAnOrderWithItems(DataTable dataTable) {
        String customerId = ctx.currentCustomerId;

        List<OrderItemCommand> items = dataTable.asMaps().stream()
                .map(row -> new OrderItemCommand(
                        ProductId.of(row.get("productId")),
                        row.get("name"),
                        Double.parseDouble(row.get("unitPrice")),
                        Integer.parseInt(row.get("quantity"))
                ))
                .toList();

        String email = ctx.currentCustomerEmail != null ? ctx.currentCustomerEmail : customerId + "@test.com";
        CreateOrderCommand command = new CreateOrderCommand(
                CustomerId.of(customerId),
                Email.of(email),
                items
        );
        ctx.lastResult = ctx.createOrderService.execute(command);
        ctx.currentOrderId = ctx.lastResult.orderId();
    }

    @When("the order is confirmed")
    public void theOrderIsConfirmed() {
        ctx.confirmOrderService.execute(OrderId.of(ctx.currentOrderId));
    }

    @When("confirmation is attempted on the empty order")
    public void confirmationIsAttemptedOnEmptyOrder() {
        try {
            ctx.confirmOrderService.execute(OrderId.of(ctx.currentOrderId));
        } catch (Exception e) {
            ctx.capturedException = e;
        }
    }

    @When("a discount of {int} percent is applied")
    public void aDiscountIsApplied(int pct) {
        Order order = ctx.orderRepository.findById(OrderId.of(ctx.currentOrderId))
                .orElseThrow(() -> new AssertionError("Order not found: " + ctx.currentOrderId));
        order.applyDiscount(pct);
        ctx.orderRepository.save(order);
    }

    // =========================================================================
    // THEN / AND — Assertions
    // =========================================================================

    @Then("the order is created with status {string}")
    public void theOrderIsCreatedWithStatus(String expectedStatus) {
        assertThat(ctx.lastResult).isNotNull();
        assertThat(ctx.lastResult.status()).isEqualTo(expectedStatus);
        assertThat(ctx.lastResult.orderId()).isNotBlank();
    }

    @And("the order total is {double} euros")
    public void theOrderTotalIs(double expectedTotal) {
        double total;
        if (ctx.lastResult != null) {
            total = ctx.lastResult.total();
        } else {
            Order order = ctx.orderRepository.findById(OrderId.of(ctx.currentOrderId))
                    .orElseThrow(() -> new AssertionError("Order not found: " + ctx.currentOrderId));
            total = order.calculateTotal();
        }
        assertThat(total).isCloseTo(expectedTotal, within(0.01));
    }

    @Then("the order status is {string}")
    public void theOrderStatusIs(String expectedStatus) {
        Order order = ctx.orderRepository.findById(OrderId.of(ctx.currentOrderId))
                .orElseThrow(() -> new AssertionError("Order not found: " + ctx.currentOrderId));
        assertThat(order.getStatus().name()).isEqualTo(expectedStatus);
    }

    @And("a confirmation notification is sent")
    public void aConfirmationNotificationIsSent() {
        assertThat(ctx.notificationAdapter.wasNotifiedFor(ctx.currentOrderId))
                .as("A notification should have been sent for order " + ctx.currentOrderId)
                .isTrue();
    }

    @Then("an error is raised with message {string}")
    public void anErrorIsRaisedWithMessage(String expectedMessage) {
        assertThat(ctx.capturedException)
                .as("An exception should have been thrown")
                .isNotNull();
        assertThat(ctx.capturedException.getMessage())
                .as("Exception message")
                .contains(expectedMessage);
    }

    @And("no notification is sent")
    public void noNotificationIsSent() {
        assertThat(ctx.notificationAdapter.count())
                .as("No notification should have been sent")
                .isEqualTo(0);
    }
}
