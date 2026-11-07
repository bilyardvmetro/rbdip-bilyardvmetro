package com.rbdip.bookstore.customer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reuses identical customer details, including when orders arrive concurrently. */
@Service
public class CustomerService {

    private static final String UPSERT_CUSTOMER = """
            INSERT INTO customers (full_name, address, phone) VALUES (?, ?, ?)
            ON CONFLICT ON CONSTRAINT customers_details_key
            DO UPDATE SET full_name = EXCLUDED.full_name
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
        Long id = jdbcTemplate.queryForObject(UPSERT_CUSTOMER, Long.class, fullName, address, phone);
        return customerRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("customer " + id + " not found after insert"));
    }
}
