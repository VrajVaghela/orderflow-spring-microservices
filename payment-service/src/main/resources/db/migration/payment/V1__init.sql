CREATE TABLE payments (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL UNIQUE,
    method varchar(16) NOT NULL,
    status varchar(16) NOT NULL,
    amount_minor bigint NOT NULL,
    currency varchar(3) NOT NULL,
    reason varchar(512),
    created_at timestamptz NOT NULL
);

CREATE TABLE outbox_events (
    id uuid PRIMARY KEY,
    aggregate_id uuid NOT NULL,
    event_type varchar(64) NOT NULL,
    topic varchar(128) NOT NULL,
    payload text NOT NULL,
    correlation_id varchar(128),
    created_at timestamptz NOT NULL,
    published_at timestamptz,
    attempts integer NOT NULL DEFAULT 0,
    last_error varchar(512)
);

CREATE INDEX idx_payment_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    event_id uuid PRIMARY KEY,
    processed_at timestamptz NOT NULL
);
