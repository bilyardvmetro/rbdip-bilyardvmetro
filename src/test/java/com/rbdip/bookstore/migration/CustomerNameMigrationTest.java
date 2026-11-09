package com.rbdip.bookstore.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CustomerNameMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void backfillsWithoutLosingNamesAndSupportsBothWritersUntilContract() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUser(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        migrate(dataSource, "2");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<String> legacyNames = List.of("Anna Sidorova", "Anna", "Anna ", "  Anna  Sidorova  ",
                " ", "", "Иванов Иван Иванович", "李 雷");
        for (String name : legacyNames) {
            jdbc.update("INSERT INTO customers (full_name) VALUES (?)", name);
        }
        jdbc.update("INSERT INTO orders (customer_id, status) VALUES (1, 'paid')");

        migrate(dataSource, "3");
        jdbc.update("INSERT INTO customers (full_name) VALUES ('Old Writer')");
        assertThat(jdbc.queryForMap("SELECT first_name, last_name FROM customers WHERE full_name = 'Old Writer'"))
                .containsEntry("first_name", "Old").containsEntry("last_name", "Writer");

        migrate(dataSource, "5");
        assertThat(fullNames(jdbc).subList(0, legacyNames.size())).containsExactlyElementsOf(legacyNames);
        assertThat(fullNames(jdbc)).endsWith("Old Writer");
        assertDualWrites(jdbc);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE first_name IS NULL OR last_name IS NULL",
                Integer.class)).isZero();

        migrate(dataSource, "6");
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns"
                + " WHERE table_schema = 'public' AND table_name = 'customers'", String.class))
                .contains("first_name", "last_name").doesNotContain("full_name");
        assertThat(fullNames(jdbc).subList(0, legacyNames.size())).containsExactlyElementsOf(legacyNames);
        assertThat(jdbc.queryForMap("SELECT customer_id, status FROM orders WHERE id = 1"))
                .containsEntry("customer_id", 1L).containsEntry("status", "paid");
        assertNewWriterAfterContract(jdbc);
        Flyway flyway = Flyway.configure().dataSource(dataSource).load();
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    private void assertDualWrites(JdbcTemplate jdbc) {
        jdbc.update("UPDATE customers SET full_name = 'Old Changed Name' WHERE full_name = 'Old Writer'");
        assertThat(jdbc.queryForMap("SELECT first_name, last_name FROM customers WHERE full_name = 'Old Changed Name'"))
                .containsEntry("first_name", "Old").containsEntry("last_name", "Changed Name");
        Long id = jdbc.queryForObject("INSERT INTO customers (first_name, last_name)"
                + " VALUES ('New', 'Writer') RETURNING id", Long.class);
        assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE id = ?", String.class, id))
                .isEqualTo("New Writer");
        Long reused = jdbc.queryForObject("INSERT INTO customers (full_name) VALUES ('New Writer')"
                + " ON CONFLICT ON CONSTRAINT customers_details_key DO UPDATE SET full_name = EXCLUDED.full_name"
                + " RETURNING id", Long.class);
        assertThat(reused).isEqualTo(id);
        jdbc.update("UPDATE customers SET first_name = 'Renamed', last_name = 'Customer' WHERE id = ?", id);
        assertThat(jdbc.queryForObject("SELECT full_name FROM customers WHERE id = ?", String.class, id))
                .isEqualTo("Renamed Customer");
    }

    private void assertNewWriterAfterContract(JdbcTemplate jdbc) {
        Long id = jdbc.queryForObject("INSERT INTO customers (first_name, last_name)"
                + " SELECT first_name, last_name FROM customer_name_parts('Final Customer')"
                + " ON CONFLICT ON CONSTRAINT customers_name_parts_key DO UPDATE SET first_name = EXCLUDED.first_name"
                + " RETURNING id", Long.class);
        assertThat(jdbc.queryForMap("SELECT first_name, last_name FROM customers WHERE id = ?", id))
                .containsEntry("first_name", "Final").containsEntry("last_name", "Customer");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO customers (first_name, last_name)"
                + " VALUES ('Final', 'Customer')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE customers SET last_name = NULL WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO customers (first_name, last_name) VALUES (?, '')",
                "x".repeat(256)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO customers (first_name, last_name) VALUES (?, ?)",
                "x".repeat(200), "y".repeat(100)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<String> fullNames(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT CASE WHEN last_name = '' THEN first_name"
                + " ELSE first_name || ' ' || last_name END FROM customers ORDER BY id", String.class);
    }

    private void migrate(DataSource dataSource, String target) {
        Flyway.configure().dataSource(dataSource).target(target).load().migrate();
    }
}
