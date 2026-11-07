package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.rbdip.bookstore.customer.CustomerRepository;
import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class OrderServiceTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService service;

    @Autowired
    private ProductRepository products;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private OrderItemRepository items;

    @Autowired
    private CustomerRepository customers;

    @MockBean
    private OrderConfirmationNotifier notifier;

    @ParameterizedTest
    @CsvSource({", 80.00", "vip, 71.00", "wholesale, 66.50"})
    void savesProductReferencesAndNotifiesWithTheCalculatedTotal(String customerType, String expectedTotal) {
        Product first = createProduct("First", "12.50");
        Product second = createProduct("Second", "20.00");
        CreateOrderRequest request = new CreateOrderRequest(
                "Anna", "Moscow", "+79990000000", customerType, "SAVE10", List.of(
                        new CreateOrderRequest.Item(first.getId(), null),
                        new CreateOrderRequest.Item(second.getId(), 2),
                        new CreateOrderRequest.Item(first.getId(), 3)));

        Order order = service.createOrder(request);

        assertThat(order.getStatus()).isEqualTo("new");
        assertThat(order.getCustomerFullName()).isEqualTo("Anna");
        assertThat(order.getCustomerAddress()).isEqualTo("Moscow");
        assertThat(order.getCustomerPhone()).isEqualTo("+79990000000");
        assertThat(orders.count()).isEqualTo(1);
        assertThat(customers.count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForList(
                "SELECT product_id FROM order_items ORDER BY id", Long.class))
                .containsExactly(first.getId(), second.getId(), first.getId());
        assertThat(items.findByOrderId(order.getId()))
                .extracting(OrderItem::getProductName, OrderItem::getProductPrice, OrderItem::getQuantity)
                .containsExactlyInAnyOrder(
                        tuple("First", new BigDecimal("12.50"), 1),
                        tuple("Second", new BigDecimal("20.00"), 2),
                        tuple("First", new BigDecimal("12.50"), 3));
        verify(notifier).sendConfirmationEmail("Anna", order.getId(), new BigDecimal(expectedTotal));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingCustomerName(String name) {
        assertRejected(new CreateOrderRequest(name, "Moscow", null, null, null, List.of()),
                "customerFullName is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingAddress(String address) {
        assertRejected(new CreateOrderRequest("Anna", address, null, null, null, List.of()),
                "customerAddress is required");
    }

    @Test
    void rejectsMissingItems() {
        assertRejected(request(null), "order must contain at least one item");
        assertRejected(request(List.of()), "order must contain at least one item");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsInvalidQuantityAfterAValidItemWithoutSavingAnything(int quantity) {
        Product product = createProduct("Book", "10.00");
        assertRejected(request(List.of(
                new CreateOrderRequest.Item(product.getId(), 1),
                new CreateOrderRequest.Item(product.getId(), quantity))), "quantity must be positive");
    }

    @Test
    void checksProductExistenceBeforeQuantityAndDoesNotSaveAPartialOrder() {
        Product product = createProduct("Book", "10.00");
        long missingId = product.getId() + 1;
        assertRejected(request(List.of(
                new CreateOrderRequest.Item(product.getId(), 1),
                new CreateOrderRequest.Item(missingId, 0))), "product " + missingId + " not found");
    }

    @Test
    void rollsBackOrderAndItemsWhenConfirmationFails() {
        Product product = createProduct("Book", "10.00");
        doThrow(new IllegalStateException("notification failed"))
                .when(notifier).sendConfirmationEmail(anyString(), anyLong(), any(BigDecimal.class));

        assertThatThrownBy(() -> service.createOrder(request(
                List.of(new CreateOrderRequest.Item(product.getId(), 1)))))
                .isInstanceOf(IllegalStateException.class).hasMessage("notification failed");

        assertThat(orders.count()).isZero();
        assertThat(items.count()).isZero();
        assertThat(customers.count()).isZero();
    }

    private void assertRejected(CreateOrderRequest request, String message) {
        assertThatThrownBy(() -> service.createOrder(request))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(message);
        assertThat(orders.count()).isZero();
        assertThat(items.count()).isZero();
        assertThat(customers.count()).isZero();
        verifyNoInteractions(notifier);
    }

    private CreateOrderRequest request(List<CreateOrderRequest.Item> requestedItems) {
        return new CreateOrderRequest("Anna", "Moscow", null, null, null, requestedItems);
    }

    private Product createProduct(String name, String price) {
        return products.save(new Product(name, new BigDecimal(price), null));
    }
}
