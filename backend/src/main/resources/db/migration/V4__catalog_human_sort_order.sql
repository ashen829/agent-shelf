-- Sort product names and SKUs the way people read them: case-insensitive, accents and digits
-- in natural order ("anchor" < "Brick" < "Portland" < "PVC"). The database default compares
-- raw characters, so every capital letter came before every lower-case one.
-- "und-x-icu" is PostgreSQL's built-in ICU root collation. Indexes on these columns are rebuilt.

ALTER TABLE catalog.product
    ALTER COLUMN name TYPE VARCHAR(200) COLLATE "und-x-icu",
    ALTER COLUMN sku TYPE VARCHAR(64) COLLATE "und-x-icu";
