package com.rbdip.bookstore.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbdip.bookstore.BookstoreApplication;
import com.rbdip.bookstore.customer.CustomerService;
import com.rbdip.bookstore.order.CreateOrderRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the same application instance before, during and after the contract migration. */
@Testcontainers
class OrderApiDuringMigrationTest {

    private static final int WORKERS = 4;
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderApiDuringMigrationTest.class);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void createsAndListsOrdersWhileFullNameIsRemoved() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).target("2").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO customers (full_name, address) VALUES ('Legacy Customer', 'Moscow')");

        // Boot the new release against the expanded schema, without contract.
        try (var context = new SpringApplicationBuilder(BookstoreApplication.class).run(
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.flyway.target=5", "--server.port=0",
                "--spring.jpa.properties.hibernate.generate_statistics=false",
                "--logging.level.com.rbdip.bookstore.order.OrderConfirmationNotifier=WARN")) {
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            TestRestTemplate rest = new TestRestTemplate(new RestTemplateBuilder()
                    .rootUri("http://localhost:" + port)
                    .setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)));
            Long productId = createProduct(rest);
            CreateOrderRequest request = new CreateOrderRequest("Legacy Customer", "Moscow", null, null, null,
                    List.of(new CreateOrderRequest.Item(productId, 1)));

            assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE id = 1", String.class))
                    .isEqualTo("Legacy Customer");
            assertMixedWriters(jdbc, context.getBean(CustomerService.class));
            int writes = runTrafficAcrossContract(dataSource, jdbc, rest, request);
            LOGGER.info("Lab3 migration load: workers={}, successfulPosts={}, successfulGets={}, errors=0, schema=V5->V6",
                    WORKERS, writes, writes);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns"
                    + " WHERE table_schema = 'public' AND table_name = 'customers'", String.class))
                    .doesNotContain("full_name");
        }
    }

    private void assertMixedWriters(JdbcTemplate jdbc, CustomerService customers) throws Exception {
        String oldWriter = "INSERT INTO customers (full_name) VALUES (?)"
                + " ON CONFLICT ON CONSTRAINT customers_details_key DO UPDATE SET full_name = EXCLUDED.full_name"
                + " RETURNING id";
        CyclicBarrier start = new CyclicBarrier(WORKERS);
        List<Future<List<Long>>> results = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(WORKERS)) {
            for (int i = 0; i < WORKERS; i++) {
                boolean legacy = i % 2 == 0;
                results.add(pool.submit(() -> {
                    List<Long> ids = new ArrayList<>();
                    for (int round = 0; round < 8; round++) {
                        start.await(5, TimeUnit.SECONDS);
                        String name = "Concurrent Customer " + round;
                        ids.add(legacy ? jdbc.queryForObject(oldWriter, Long.class, name)
                                : customers.findOrCreate(name, null, null).getId());
                    }
                    return ids;
                }));
            }
            List<List<Long>> ids = new ArrayList<>();
            for (Future<List<Long>> result : results) {
                ids.add(result.get(15, TimeUnit.SECONDS));
            }
            for (int round = 0; round < 8; round++) {
                Long expected = ids.getFirst().get(round);
                assertThat(expected).isNotNull();
                for (List<Long> worker : ids) {
                    assertThat(worker.get(round)).isEqualTo(expected);
                }
            }
        }
        assertThat(jdbc.update("DELETE FROM customers WHERE first_name = 'Concurrent' AND last_name LIKE 'Customer %'"))
                .isEqualTo(8);
        LOGGER.info("Mixed customer writers: rounds=8, writes=32, errors=0");
    }

    private int runTrafficAcrossContract(
            DataSource dataSource, JdbcTemplate jdbc, TestRestTemplate rest, CreateOrderRequest request) throws Exception {
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean contracted = new AtomicBoolean(false);
        AtomicInteger writes = new AtomicInteger();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        CountDownLatch beforeContract = new CountDownLatch(WORKERS);
        CountDownLatch afterContract = new CountDownLatch(WORKERS);
        List<Future<?>> tasks = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(WORKERS)) {
            for (int i = 0; i < WORKERS; i++) {
                tasks.add(pool.submit(() -> sendRequests(
                        rest, request, running, contracted, writes, errors, beforeContract, afterContract)));
            }
            try {
                assertThat(beforeContract.await(20, TimeUnit.SECONDS)).as("Traffic before contract: %s", errors).isTrue();
                assertThat(errors).isEmpty();
                assertThat(Flyway.configure().dataSource(dataSource).load().migrate().migrationsExecuted).isEqualTo(1);
                contracted.set(true);
                assertThat(afterContract.await(20, TimeUnit.SECONDS)).as("Traffic after contract: %s", errors).isTrue();
            } finally {
                running.set(false);
                for (Future<?> task : tasks) {
                    task.get(15, TimeUnit.SECONDS);
                }
            }
        }
        assertThat(errors).as("No API errors during the migration").isEmpty();
        assertThat(writes.get()).isGreaterThanOrEqualTo(WORKERS * 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(writes.get());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items", Integer.class)).isEqualTo(writes.get());
        return writes.get();
    }

    private void sendRequests(TestRestTemplate rest, CreateOrderRequest request,
            AtomicBoolean running, AtomicBoolean contracted, AtomicInteger writes, List<Throwable> errors,
            CountDownLatch beforeContract, CountDownLatch afterContract) {
        boolean reportedBefore = false;
        boolean reportedAfter = false;
        try {
            while (running.get()) {
                // A round starting after commit must use the contracted schema for both requests.
                boolean after = contracted.get();
                ResponseEntity<Map> created = rest.postForEntity("/orders", request, Map.class);
                assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(created.getBody()).containsEntry("status", "new").containsKey("id");
                writes.incrementAndGet();
                assertOrders(rest);
                if (!reportedBefore) {
                    beforeContract.countDown();
                    reportedBefore = true;
                }
                if (after && !reportedAfter) {
                    afterContract.countDown();
                    reportedAfter = true;
                }
            }
        } catch (RuntimeException | AssertionError failure) {
            errors.add(failure);
            beforeContract.countDown();
            afterContract.countDown();
        }
    }

    private void assertOrders(TestRestTemplate rest) {
        ResponseEntity<List> response = rest.getForEntity("/orders", List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        for (Object value : response.getBody()) {
            Map<?, ?> order = (Map<?, ?>) value;
            assertThat(order.get("customerFullName")).isEqualTo("Legacy Customer");
            assertThat(order.get("status")).isEqualTo("new");
            assertThat((List<?>) order.get("items"))
                    .isEqualTo(List.of(Map.of("productName", "Book", "quantity", 1)));
        }
    }

    private Long createProduct(TestRestTemplate rest) {
        ResponseEntity<Map> response = rest.postForEntity("/products", Map.of("name", "Book", "price", "10.00"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Long.valueOf(response.getBody().get("id").toString());
    }
}
