package com.rbdip.bookstore.order;

import org.springframework.stereotype.Component;

/** Validates customer details and quantities without writing data. */
@Component
public class OrderValidator {

    private static final int DEFAULT_QUANTITY = 1;

    public void validate(CreateOrderRequest request) {
        requireText(request.customerFullName(), "customerFullName");
        requireText(request.customerAddress(), "customerAddress");
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("order must contain at least one item");
        }
    }

    public int resolveQuantity(Integer requestedQuantity) {
        int quantity = requestedQuantity == null ? DEFAULT_QUANTITY : requestedQuantity;
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        return quantity;
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
