-- Product search: words anywhere in SKU, name or category, ignoring case.

-- Trigram matching makes "contains" searches (LIKE '%pvc%') use an index instead of reading
-- every row. A standard contrib extension, shipped with PostgreSQL.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- The text a search looks in, kept up to date by PostgreSQL itself. Searching this one column
-- guarantees that the query and the index below always match.
ALTER TABLE catalog.product
    ADD COLUMN search_text TEXT GENERATED ALWAYS AS (
        lower(sku) || ' ' || lower(name) || ' ' || coalesce(lower(category), '')
    ) STORED;

CREATE INDEX product_search_trgm_ix ON catalog.product USING gin (search_text gin_trgm_ops);

-- Filters and sort orders of the product list: category, price.
CREATE INDEX product_supplier_status_category_ix ON catalog.product (supplier_id, status, category);
CREATE INDEX product_supplier_status_price_ix ON catalog.product (supplier_id, status, price_amount);
