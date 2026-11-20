-- AI cleanup suggestions (M2, ADR 0006). The enrichment module owns this schema. Products live in
-- the catalog module and are referenced by ID only (no cross-module foreign keys).
CREATE SCHEMA IF NOT EXISTS enrichment;

-- One run of AI cleanup over a supplier's catalog. batch_id is the Claude Message Batches ID.
-- Token counts are stored for every job, so the cost of each run is known.
CREATE TABLE enrichment.enrichment_job (
    id                  UUID         PRIMARY KEY,
    supplier_id         UUID         NOT NULL,
    status              VARCHAR(20)  NOT NULL CHECK (status IN
                          ('QUEUED', 'SUBMITTED', 'COMPLETED', 'FAILED')),
    model               VARCHAR(100) NOT NULL,
    batch_id            VARCHAR(100),
    products_total      INT          NOT NULL DEFAULT 0 CHECK (products_total >= 0),
    products_failed     INT          NOT NULL DEFAULT 0 CHECK (products_failed >= 0),
    suggestions_created INT          NOT NULL DEFAULT 0 CHECK (suggestions_created >= 0),
    suggestions_dropped INT          NOT NULL DEFAULT 0 CHECK (suggestions_dropped >= 0),
    input_tokens        BIGINT       NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
    output_tokens       BIGINT       NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
    cache_read_tokens   BIGINT       NOT NULL DEFAULT 0 CHECK (cache_read_tokens >= 0),
    cache_write_tokens  BIGINT       NOT NULL DEFAULT 0 CHECK (cache_write_tokens >= 0),
    failure_message     VARCHAR(500),
    submitted_at        TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL
);

-- Job history, newest first.
CREATE INDEX enrichment_job_supplier_created_ix
    ON enrichment.enrichment_job (supplier_id, created_at DESC);
-- At most one job waiting or running per supplier, even with many backend instances.
CREATE UNIQUE INDEX enrichment_job_one_active_uk ON enrichment.enrichment_job (supplier_id)
    WHERE status IN ('QUEUED', 'SUBMITTED');

-- One suggested change to one product field. product_version is the product's version when the
-- suggestion was made, so approving a suggestion for a product edited since then can be refused.
CREATE TABLE enrichment.product_suggestion (
    id                UUID          PRIMARY KEY,
    supplier_id       UUID          NOT NULL,
    enrichment_job_id UUID          NOT NULL
                        REFERENCES enrichment.enrichment_job (id) ON DELETE CASCADE,
    product_id        UUID          NOT NULL,
    product_version   BIGINT        NOT NULL,
    field             VARCHAR(20)   NOT NULL CHECK (field IN
                        ('NAME', 'CATEGORY', 'UNIT', 'DESCRIPTION')),
    old_value         VARCHAR(2000),
    new_value         VARCHAR(2000) NOT NULL,
    reason            VARCHAR(300)  NOT NULL,
    confidence        NUMERIC(3, 2) NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    status            VARCHAR(20)   NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    version           BIGINT        NOT NULL,
    UNIQUE (enrichment_job_id, product_id, field)
);

-- A job's suggestions, grouped by product (the review screen's order).
CREATE INDEX product_suggestion_job_product_ix
    ON enrichment.product_suggestion (supplier_id, enrichment_job_id, product_id, field);
