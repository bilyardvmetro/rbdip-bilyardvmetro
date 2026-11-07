package com.rbdip.bookstore.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NormalizationMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migratesExistingOrdersWithoutLosingCustomerOrProductData() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).target("1").load().migrate();
        new ResourceDatabasePopulator(new ClassPathResource("migration-test/legacy_orders.sql"))
                .execute(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<String> legacyOrders = orderDetails(jdbc, false);
        List<String> legacyItems = itemDetails(jdbc, false);

        Flyway flyway = Flyway.configure().dataSource(dataSource).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);

        assertThat(orderDetails(jdbc, true)).containsExactlyElementsOf(legacyOrders);
        assertThat(itemDetails(jdbc, true)).containsExactlyElementsOf(legacyItems);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(6);
        Long missingProduct = jdbc.queryForObject(
                "SELECT id FROM products WHERE name = 'Missing book' AND price = 30.00", Long.class);
        Long historicalProduct = jdbc.queryForObject(
                "SELECT id FROM products WHERE name = 'Changed price' AND price = 40.00", Long.class);
        assertThat(jdbc.queryForList("SELECT product_id FROM order_items ORDER BY id", Long.class))
                .containsExactly(1L, 1L, 3L, missingProduct, missingProduct, historicalProduct);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT customer_id) FROM orders WHERE id IN (1, 2)",
                Integer.class)).isEqualTo(1);
        assertReferentialIntegrity(jdbc);
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    private List<String> orderDetails(JdbcTemplate jdbc, boolean normalized) {
        String sql = normalized
                ? "SELECT o.id, c.full_name, c.address, c.phone, o.status, o.created_at"
                        + " FROM orders o JOIN customers c ON c.id = o.customer_id ORDER BY o.id"
                : "SELECT id, customer_full_name, customer_address, customer_phone, status, created_at"
                        + " FROM orders ORDER BY id";
        return jdbc.query(sql, (row, index) -> row.getLong(1) + "|" + row.getString(2) + "|"
                + row.getString(3) + "|" + row.getString(4) + "|" + row.getString(5) + "|" + row.getTimestamp(6));
    }

    private List<String> itemDetails(JdbcTemplate jdbc, boolean normalized) {
        String sql = normalized
                ? "SELECT i.id, i.order_id, p.name, p.price, i.quantity"
                        + " FROM order_items i JOIN products p ON p.id = i.product_id ORDER BY i.id"
                : "SELECT id, order_id, product_name, product_price, quantity FROM order_items ORDER BY id";
        return jdbc.query(sql, (row, index) -> row.getLong(1) + "|" + row.getLong(2) + "|"
                + row.getString(3) + "|" + row.getBigDecimal(4) + "|" + row.getInt(5));
    }

    private void assertReferentialIntegrity(JdbcTemplate jdbc) {
        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET customer_id = NULL WHERE id = 1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET customer_id = -1 WHERE id = 1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE order_items SET product_id = NULL WHERE id = 1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE order_items SET product_id = -1 WHERE id = 1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM customers WHERE id IN (SELECT customer_id FROM orders)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM products WHERE id = 1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO customers (full_name, address, phone) VALUES ('Anna', 'Moscow', NULL)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
