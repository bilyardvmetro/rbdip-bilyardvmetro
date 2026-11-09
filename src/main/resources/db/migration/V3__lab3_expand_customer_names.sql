ALTER TABLE customers
    ADD COLUMN first_name VARCHAR(255),
    ADD COLUMN last_name VARCHAR(255);

-- Split at the first literal space. A single name (including a lone trailing
-- space) stays in first_name. This convention preserves every original string,
-- including repeated spaces, without merging previously distinct customers.
CREATE FUNCTION customer_name_parts(raw_name TEXT)
RETURNS TABLE(first_name TEXT, last_name TEXT)
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$
    SELECT
        CASE WHEN separator BETWEEN 1 AND char_length(raw_name) - 1
            THEN left(raw_name, separator - 1) ELSE raw_name END,
        CASE WHEN separator BETWEEN 1 AND char_length(raw_name) - 1
            THEN substr(raw_name, separator + 1) ELSE '' END
    FROM (SELECT strpos(raw_name, ' ') AS separator) parts;
$$;

-- Old instances write full_name; new instances write the name parts. Keep both
-- representations current for the entire compatibility window. If an update
-- explicitly changes full_name, the old representation takes precedence.
CREATE FUNCTION sync_customer_name_versions()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF (TG_OP = 'INSERT' AND NEW.full_name IS NOT NULL)
        OR (TG_OP = 'UPDATE' AND NEW.full_name IS DISTINCT FROM OLD.full_name)
        OR NEW.first_name IS NULL OR NEW.last_name IS NULL THEN
        SELECT parts.first_name, parts.last_name
        INTO NEW.first_name, NEW.last_name
        FROM customer_name_parts(NEW.full_name) parts;
    ELSE
        NEW.full_name := CASE WHEN NEW.last_name = '' THEN NEW.first_name
            ELSE NEW.first_name || ' ' || NEW.last_name END;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER customers_sync_name_versions
BEFORE INSERT OR UPDATE ON customers
FOR EACH ROW EXECUTE FUNCTION sync_customer_name_versions();
