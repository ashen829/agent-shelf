-- Background jobs (ADR 0003) and CSV imports for the catalog module.

-- db-scheduler's table, from postgresql_tables.sql in db-scheduler 17.0.0. Its own schema:
-- it holds only task names, times and job IDs, never supplier data.
CREATE SCHEMA IF NOT EXISTS jobs;

CREATE TABLE jobs.scheduled_tasks (
    task_name            TEXT                     NOT NULL,
    task_instance        TEXT                     NOT NULL,
    task_data            BYTEA,
    execution_time       TIMESTAMP WITH TIME ZONE NOT NULL,
    picked               BOOLEAN                  NOT NULL,
    picked_by            TEXT,
    last_success         TIMESTAMP WITH TIME ZONE,
    last_failure         TIMESTAMP WITH TIME ZONE,
    consecutive_failures INT,
    last_heartbeat       TIMESTAMP WITH TIME ZONE,
    version              BIGINT                   NOT NULL,
    priority             SMALLINT,
    PRIMARY KEY (task_name, task_instance)
);

CREATE INDEX execution_time_idx ON jobs.scheduled_tasks (execution_time);
CREATE INDEX last_heartbeat_idx ON jobs.scheduled_tasks (last_heartbeat);
CREATE INDEX priority_execution_time_idx ON jobs.scheduled_tasks (priority DESC, execution_time ASC);

-- One CSV import: what the supplier uploaded, how its columns map to product fields, and the
-- result. Column mappings are 0-based column indexes in the file; NULL means "not in the file".
CREATE TABLE catalog.import_job (
    id                 UUID         PRIMARY KEY,
    supplier_id        UUID         NOT NULL,
    file_name          VARCHAR(255) NOT NULL,
    delimiter          CHAR(1)      NOT NULL,
    rows_total         INT          NOT NULL CHECK (rows_total >= 0),
    status             VARCHAR(30)  NOT NULL CHECK (status IN
                         ('UPLOADED', 'QUEUED', 'RUNNING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED')),
    column_sku         INT,
    column_name        INT,
    column_description INT,
    column_category    INT,
    column_unit        INT,
    column_price       INT,
    column_stock_qty   INT,
    decimal_separator  CHAR(1)      CHECK (decimal_separator IN ('.', ',')),
    complete_catalog   BOOLEAN      NOT NULL DEFAULT FALSE,
    rows_processed     INT          NOT NULL DEFAULT 0,
    rows_created       INT          NOT NULL DEFAULT 0,
    rows_updated       INT          NOT NULL DEFAULT 0,
    rows_unchanged     INT          NOT NULL DEFAULT 0,
    rows_failed        INT          NOT NULL DEFAULT 0,
    products_archived  INT          NOT NULL DEFAULT 0,
    failure_message    VARCHAR(500),
    started_at         TIMESTAMPTZ,
    finished_at        TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL
);

-- Import history, newest first.
CREATE INDEX import_job_supplier_created_ix ON catalog.import_job (supplier_id, created_at DESC);
-- At most one import queued or running per supplier, even with many backend instances.
CREATE UNIQUE INDEX import_job_one_active_uk ON catalog.import_job (supplier_id)
    WHERE status IN ('QUEUED', 'RUNNING');

-- The uploaded file. In PostgreSQL for now, behind the ImportFileStore port.
CREATE TABLE catalog.import_file (
    import_job_id UUID      PRIMARY KEY REFERENCES catalog.import_job (id) ON DELETE CASCADE,
    supplier_id   UUID      NOT NULL,
    content       BYTEA     NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL
);

-- One row that could not be imported. raw_record is the original row as CSV text, so the
-- supplier can download the failed rows, fix them and upload them again.
CREATE TABLE catalog.import_error (
    id            UUID         PRIMARY KEY,
    import_job_id UUID         NOT NULL REFERENCES catalog.import_job (id) ON DELETE CASCADE,
    supplier_id   UUID         NOT NULL,
    row_number    INT          NOT NULL,
    field         VARCHAR(30),
    message       VARCHAR(500) NOT NULL,
    raw_record    TEXT         NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX import_error_job_row_ix ON catalog.import_error (import_job_id, row_number);
