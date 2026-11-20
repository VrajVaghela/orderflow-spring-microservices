CREATE TABLE stock_items (
    sku varchar(64) PRIMARY KEY,
    available integer NOT NULL CHECK (available >= 0),
    reserved integer NOT NULL CHECK (reserved >= 0),
    version bigint NOT NULL
);

CREATE TABLE reservations (
    order_id varchar(64) PRIMARY KEY,
    reservation_id varchar(64) NOT NULL,
    status varchar(16) NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE reservation_lines (
    order_id varchar(64) NOT NULL REFERENCES reservations (order_id),
    sku varchar(64) NOT NULL,
    quantity integer NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (order_id, sku)
);

INSERT INTO stock_items (sku, available, reserved, version) VALUES
    ('SKU-100', 1000000, 0, 0),
    ('SKU-200', 1000000, 0, 0),
    ('SKU-LOW', 1, 0, 0);
