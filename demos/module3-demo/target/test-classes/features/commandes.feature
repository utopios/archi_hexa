Feature: Order management
  As a customer
  I want to create and manage my orders
  So that I can place my purchases online

  Background:
    Given the system is initialized with in-memory adapters

  Scenario: Successful order creation with multiple items
    Given a customer "CLIENT-42" with email "client@test.com"
    When they create an order with the following items:
      | productId | name     | unitPrice | quantity |
      | PROD-001  | Keyboard | 89.99     | 1        |
      | PROD-002  | Mouse    | 49.99     | 2        |
    Then the order is created with status "DRAFT"
    And the order total is 189.97 euros

  Scenario: Order confirmation — status changes to CONFIRMED
    Given an existing DRAFT order for customer "CLIENT-10"
    And the order contains an item "Keyboard" at 89.99 euros
    When the order is confirmed
    Then the order status is "CONFIRMED"
    And a confirmation notification is sent

  Scenario: Confirmation fails — empty order
    Given an empty DRAFT order for customer "CLIENT-20"
    When confirmation is attempted on the empty order
    Then an error is raised with message "aucun article"
    And no notification is sent

  Scenario: 10% discount applied on a 200 euros order
    Given an order with item "Monitor" at 200.0 euros for customer "CLIENT-30"
    When a discount of 10 percent is applied
    Then the order total is 180.0 euros
