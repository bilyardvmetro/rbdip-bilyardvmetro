-- The original full_name constraint remains available to old instances.
SET LOCAL lock_timeout = '2s';
ALTER TABLE customers ADD CONSTRAINT customers_name_parts_key
    UNIQUE NULLS NOT DISTINCT (first_name, last_name, address, phone);
