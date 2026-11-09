-- Apply only after all instances using full_name have been drained. The new
-- application uses only the name parts and customers_name_parts_key.
SET LOCAL lock_timeout = '2s';
DROP TRIGGER customers_sync_name_versions ON customers;
DROP FUNCTION sync_customer_name_versions();

ALTER TABLE customers
    DROP CONSTRAINT customers_details_key,
    DROP COLUMN full_name;
