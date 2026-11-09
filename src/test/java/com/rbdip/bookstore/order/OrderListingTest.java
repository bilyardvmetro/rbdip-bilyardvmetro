package com.rbdip.bookstore.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.customer.Customer;
import com.rbdip.bookstore.customer.CustomerService;
import com.rbdip.bookstore.product.Product;
import com.rbdip.bookstore.product.ProductRepository;
import com.rbdip.bookstore.reference.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OrderListingTest extends AbstractIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderListingTest.class);

    @Autowired
    private OrderService service;

    @Autowired
    private ProductRepository products;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private CustomerService customers;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 25})
    void listsEveryOrderAndLineWithOneQueryRegardlessOfOrderCount(int orderCount) {
        Product first = products.save(new Product("First", new BigDecimal("10.00"), null));
        Product second = products.save(new Product("Second", new BigDecimal("20.00"), null));
        List<OrderSummary> expected = new ArrayList<>();
        for (int i = 0; i < orderCount; i++) {
            String customer = "Customer " + i;
            Order order = service.createOrder(new CreateOrderRequest(customer, "Moscow", null, null, null, List.of(
                    new CreateOrderRequest.Item(first.getId(), 1),
                    new CreateOrderRequest.Item(second.getId(), 2),
                    new CreateOrderRequest.Item(first.getId(), 3))));
            expected.add(new OrderSummary(order.getId(), customer, "new", List.of(
                    new OrderSummary.Item("First", 1),
                    new OrderSummary.Item("Second", 2),
                    new OrderSummary.Item("First", 3))));
        }
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        ResponseEntity<OrderSummary[]> response = restTemplate.getForEntity("/orders", OrderSummary[].class);

        long queries = statistics.getPrepareStatementCount();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactlyElementsOf(expected);
        assertThat(queries).as("SQL count for %d orders", orderCount).isEqualTo(1);
        LOGGER.info("Order listing: orders={}, items={}, SQL={}", orderCount, orderCount * 3, queries);
    }

    @Test
    void includesOrdersWithoutItemsAndReturnsAnEmptyListForAnEmptyDatabase() {
        assertThat(restTemplate.getForEntity("/orders", OrderSummary[].class).getBody()).isEmpty();
        Customer customer = customers.findOrCreate("Legacy Customer", "Moscow", null);
        Order order = orders.save(new Order(customer, "paid"));

        ResponseEntity<OrderSummary[]> response = restTemplate.getForEntity("/orders", OrderSummary[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsExactly(new OrderSummary(order.getId(), "Legacy Customer", "paid", List.of()));
    }
}
