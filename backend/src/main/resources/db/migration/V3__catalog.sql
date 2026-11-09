-- catalog module: the supplier's products.

CREATE SCHEMA IF NOT EXISTS catalog;

CREATE TABLE catalog.product (
    id             UUID          PRIMARY KEY,
    -- Tenant: every query is scoped by it. No foreign key to identity (modules reference by ID).
    supplier_id    UUID          NOT NULL,
    sku            VARCHAR(64)   NOT NULL,
    name           VARCHAR(200)  NOT NULL,
    description    VARCHAR(2000),
    category       VARCHAR(100),
    unit           VARCHAR(30),
    -- Up to 4 decimal places: wholesale unit prices are often fractions of a cent.
    price_amount   NUMERIC(17,4) NOT NULL CHECK (price_amount >= 0),
    price_currency VARCHAR(3)    NOT NULL CHECK (price_currency ~ '^[A-Z]{3}$'),
    stock_qty      INTEGER       NOT NULL CHECK (stock_qty >= 0),
    status         VARCHAR(20)   NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    version        BIGINT        NOT NULL
);

-- A SKU is unique per supplier, ignoring case. Two suppliers may both have "PVC-20".
CREATE UNIQUE INDEX product_supplier_sku_uk ON catalog.product (supplier_id, lower(sku));

-- The product list: one supplier, one status, sorted by name or by last change.
CREATE INDEX product_supplier_status_name_ix ON catalog.product (supplier_id, status, name);
CREATE INDEX product_supplier_status_updated_ix ON catalog.product (supplier_id, status, updated_at);
