-- Duplicate catalog entries and an existing product whose price has changed.
INSERT INTO products (name, price, description) VALUES
    ('Same book', 10.00, 'first catalog entry'),
    ('Same book', 10.00, 'second catalog entry'),
    ('Other book', 20.00, 'unchanged'),
    ('Changed price', 99.00, 'new catalog price');

INSERT INTO orders (customer_full_name, customer_address, customer_phone, status, created_at) VALUES
    ('Anna', 'Moscow', NULL, 'new', '2026-01-01 10:00:00'),
    ('Anna', 'Moscow', NULL, 'paid', '2026-01-02 11:00:00'),
    ('Anna', 'Moscow', '', 'new', '2026-01-03 12:00:00'),
    ('Anna', 'Other address', NULL, 'new', '2026-01-04 13:00:00'),
    ('Legacy customer', NULL, NULL, 'new', '2026-01-05 14:00:00');

-- A missing product appears in multiple orders; its name and price must survive.
INSERT INTO order_items (order_id, product_name, product_price, quantity) VALUES
    (1, 'Same book', 10.00, 2),
    (2, 'Same book', 10.00, 1),
    (3, 'Other book', 20.00, 3),
    (4, 'Missing book', 30.00, 4),
    (5, 'Missing book', 30.00, 5),
    (1, 'Changed price', 40.00, 1);
