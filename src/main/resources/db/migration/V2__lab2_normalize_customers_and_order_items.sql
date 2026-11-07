-- Preserve the V1 customer details, including nullable contacts. Until the
-- application has a customer identifier, only identical contact tuples merge.
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    phone VARCHAR(50),
    CONSTRAINT customers_details_key UNIQUE NULLS NOT DISTINCT (full_name, address, phone)
);

INSERT INTO customers (full_name, address, phone)
SELECT DISTINCT customer_full_name, customer_address, customer_phone
FROM orders;

ALTER TABLE orders ADD COLUMN customer_id BIGINT REFERENCES customers(id);

UPDATE orders o
SET customer_id = c.id
FROM customers c
WHERE c.full_name = o.customer_full_name
    AND c.address IS NOT DISTINCT FROM o.customer_address
    AND c.phone IS NOT DISTINCT FROM o.customer_phone;

ALTER TABLE orders ALTER COLUMN customer_id SET NOT NULL;
CREATE INDEX orders_customer_id_idx ON orders(customer_id);

-- V1 has no product_id. Match the available name/price pair; if the original
-- product was changed or deleted, retain that pair as a separate catalog entry.
INSERT INTO products (name, price)
SELECT DISTINCT i.product_name, i.product_price
FROM order_items i
WHERE NOT EXISTS (
    SELECT 1 FROM products p
    WHERE p.name = i.product_name AND p.price = i.product_price
)
ORDER BY i.product_name, i.product_price;

ALTER TABLE order_items ADD COLUMN product_id BIGINT REFERENCES products(id);

-- Duplicate catalog entries cannot be distinguished using V1 data. Choose
-- the smallest matching id deterministically without deleting any products.
UPDATE order_items i
SET product_id = (
    SELECT MIN(p.id) FROM products p
    WHERE p.name = i.product_name AND p.price = i.product_price
);

ALTER TABLE order_items ALTER COLUMN product_id SET NOT NULL;
CREATE INDEX order_items_product_id_idx ON order_items(product_id);

ALTER TABLE orders
    DROP COLUMN customer_full_name,
    DROP COLUMN customer_address,
    DROP COLUMN customer_phone;

ALTER TABLE order_items
    DROP COLUMN product_name,
    DROP COLUMN product_price;
