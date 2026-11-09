package com.rbdip.bookstore.customer;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reuses identical customer details, including when orders arrive concurrently. */
@Service
public class CustomerService {

    private static final String INSERT_CUSTOMER = """
            INSERT INTO customers (first_name, last_name, address, phone)
            SELECT first_name, last_name, ?, ? FROM customer_name_parts(?)
            ON CONFLICT DO NOTHING
            RETURNING id
            """;

    private static final String FIND_CUSTOMER = """
            SELECT c.id FROM customers c
            JOIN customer_name_parts(?) n ON c.first_name = n.first_name AND c.last_name = n.last_name
            WHERE c.address IS NOT DISTINCT FROM ? AND c.phone IS NOT DISTINCT FROM ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final CustomerRepository customerRepository;

    public CustomerService(JdbcTemplate jdbcTemplate, CustomerRepository customerRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer findOrCreate(String fullName, String address, String phone) {
        // During expand-contract both unique constraints can reject the insert.
        // An untargeted DO NOTHING handles either one. Use a separate statement
        // to see the concurrent winner's commit under READ COMMITTED.
        List<Long> inserted = jdbcTemplate.query(INSERT_CUSTOMER,
                (row, index) -> row.getLong("id"), address, phone, fullName);
        Long id = inserted.isEmpty()
                ? jdbcTemplate.queryForObject(FIND_CUSTOMER, Long.class, fullName, address, phone)
                : inserted.getFirst();
        return customerRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("customer " + id + " not found after insert"));
    }
}
