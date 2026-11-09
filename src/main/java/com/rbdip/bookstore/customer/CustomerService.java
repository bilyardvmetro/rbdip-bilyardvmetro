package com.rbdip.bookstore.customer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reuses identical customer details, including when orders arrive concurrently. */
@Service
public class CustomerService {

    private static final String UPSERT_CUSTOMER = """
            INSERT INTO customers (first_name, last_name, address, phone)
            SELECT first_name, last_name, ?, ? FROM customer_name_parts(?)
            ON CONFLICT ON CONSTRAINT customers_name_parts_key
            DO UPDATE SET first_name = EXCLUDED.first_name
            RETURNING id
            """;

    private final JdbcTemplate jdbcTemplate;
    private final CustomerRepository customerRepository;

    public CustomerService(JdbcTemplate jdbcTemplate, CustomerRepository customerRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer findOrCreate(String fullName, String address, String phone) {
        Long id = jdbcTemplate.queryForObject(UPSERT_CUSTOMER, Long.class, address, phone, fullName);
        return customerRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("customer " + id + " not found after insert"));
    }
}
