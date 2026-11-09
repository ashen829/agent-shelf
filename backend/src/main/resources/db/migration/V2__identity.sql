-- identity module: suppliers (tenants), their users, and HTTP sessions.

CREATE TABLE identity.supplier (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(60)  NOT NULL,
    currency   VARCHAR(3)   NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL,
    CONSTRAINT supplier_slug_uk UNIQUE (slug)
);

CREATE TABLE identity.app_user (
    id            UUID         PRIMARY KEY,
    supplier_id   UUID         NOT NULL REFERENCES identity.supplier (id),
    -- Stored lower-cased, so the unique constraint is case-insensitive.
    email         VARCHAR(254) NOT NULL CHECK (email = lower(email)),
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('OWNER', 'STAFF')),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL,
    CONSTRAINT app_user_email_uk UNIQUE (email)
);

CREATE INDEX app_user_supplier_id_ix ON identity.app_user (supplier_id);

-- Spring Session JDBC (schema-postgresql.sql from spring-session-jdbc 4.1.1), in the identity schema.
CREATE TABLE identity.spring_session (
    primary_id            CHAR(36)     NOT NULL,
    session_id            CHAR(36)     NOT NULL,
    creation_time         BIGINT       NOT NULL,
    last_access_time      BIGINT       NOT NULL,
    max_inactive_interval INT          NOT NULL,
    expiry_time           BIGINT       NOT NULL,
    principal_name        VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON identity.spring_session (session_id);
CREATE INDEX spring_session_ix2 ON identity.spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON identity.spring_session (principal_name);

CREATE TABLE identity.spring_session_attributes (
    session_primary_id CHAR(36)     NOT NULL,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA        NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES identity.spring_session (primary_id) ON DELETE CASCADE
);
