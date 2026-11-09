package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.api.PurchaseHistory;
import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class PurchaseHistoryTest extends AbstractIntegrationTest {

    @Autowired
    private PurchaseHistory history;

    @Autowired
    private ProductRepository products;

    @Autowired
    private OrderService orders;

    @Test
    void matchesBothTheCustomerAndProductIdentifier() {
        Product first = createProduct();
        Product second = createProduct();
        purchase(first, "Anna Sidorova");

        assertThat(history.hasPurchased(first.getId(), "Anna Sidorova")).isTrue();
        assertThat(history.hasPurchased(second.getId(), "Anna Sidorova")).isFalse();
        assertThat(history.hasPurchased(first.getId(), "Ivan Petrov")).isFalse();
        purchase(second, "Ivan Petrov");
        assertThat(history.hasPurchased(second.getId(), "Ivan Petrov")).isTrue();
        assertThat(history.hasPurchased(first.getId(), "Ivan Petrov")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Anna", "Anna ", "  Anna  Sidorova  ", "Иванов Иван Иванович"})
    void usesTheSameFullNameConventionAsTheOrderApi(String customerName) {
        Product product = createProduct();
        purchase(product, customerName);

        assertThat(history.hasPurchased(product.getId(), customerName)).isTrue();
        assertThat(history.hasPurchased(product.getId(), customerName + " other")).isFalse();
    }

    @Test
    void returnsFalseForMissingPurchaseOrUnidentifiedCustomer() {
        Product product = createProduct();

        assertThat(history.hasPurchased(product.getId(), "Anna")).isFalse();
        assertThat(history.hasPurchased(product.getId(), null)).isFalse();
        assertThat(history.hasPurchased(null, "Anna")).isFalse();
    }

    private Product createProduct() {
        return products.save(new Product("Same book", new BigDecimal("10.00"), null));
    }

    private void purchase(Product product, String customerName) {
        orders.createOrder(new CreateOrderRequest(customerName, "Moscow", null, null, null,
                List.of(new CreateOrderRequest.Item(product.getId(), 1))));
    }
}
