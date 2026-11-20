CREATE TABLE orders (
    id uuid PRIMARY KEY,
    customer_id varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    currency varchar(3) NOT NULL,
    total_minor bigint NOT NULL,
    payment_method varchar(16) NOT NULL,
    payment_token varchar(128),
    reservation_id varchar(64),
    failure_reason varchar(512),
    correlation_id varchar(128) NOT NULL,
    aggregate_version bigint NOT NULL,
    lock_version bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX idx_orders_customer ON orders (customer_id, created_at DESC);
CREATE INDEX idx_orders_status_updated ON orders (status, updated_at);

CREATE TABLE order_lines (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES orders (id),
    sku varchar(64) NOT NULL,
    quantity integer NOT NULL,
    unit_price_minor bigint NOT NULL,
    line_index integer NOT NULL
);

CREATE TABLE outbox_events (
    id uuid PRIMARY KEY,
    aggregate_id uuid NOT NULL,
    aggregate_type varchar(64) NOT NULL,
    event_type varchar(64) NOT NULL,
    topic varchar(128) NOT NULL,
    payload text NOT NULL,
    correlation_id varchar(128),
    created_at timestamptz NOT NULL,
    published_at timestamptz,
    attempts integer NOT NULL DEFAULT 0,
    last_error varchar(512)
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    event_id uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL
);

CREATE TABLE idempotency_keys (
    key varchar(80) PRIMARY KEY,
    order_id uuid NOT NULL,
    request_hash char(64) NOT NULL,
    created_at timestamptz NOT NULL
);
