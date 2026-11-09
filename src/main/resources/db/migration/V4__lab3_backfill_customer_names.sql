UPDATE customers c
SET (first_name, last_name) = (
    SELECT parts.first_name, parts.last_name FROM customer_name_parts(c.full_name) parts
)
WHERE c.first_name IS NULL OR c.last_name IS NULL;

-- Validate with a weaker lock before the brief SET NOT NULL operation.
ALTER TABLE customers ADD CONSTRAINT customers_names_present
    CHECK (first_name IS NOT NULL AND last_name IS NOT NULL) NOT VALID;
ALTER TABLE customers VALIDATE CONSTRAINT customers_names_present;
ALTER TABLE customers
    ALTER COLUMN first_name SET NOT NULL,
    ALTER COLUMN last_name SET NOT NULL;
ALTER TABLE customers DROP CONSTRAINT customers_names_present;

-- Preserve the old full_name length limit after its column is removed.
ALTER TABLE customers ADD CONSTRAINT customers_name_length
    CHECK (char_length(first_name)
        + CASE WHEN last_name = '' THEN 0 ELSE 1 + char_length(last_name) END <= 255) NOT VALID;
ALTER TABLE customers VALIDATE CONSTRAINT customers_name_length;
