CREATE TABLE IF NOT EXISTS payments (
                                        id            VARCHAR(64) PRIMARY KEY,
    merchant_id   VARCHAR(64) NOT NULL,
    amount_minor  BIGINT      NOT NULL,   -- whole minor units, never floating point
    currency      VARCHAR(3)  NOT NULL,
    recorded_at   TIMESTAMP WITH TIME ZONE NOT NULL
                                );

CREATE INDEX IF NOT EXISTS idx_payments_merchant_id ON payments (merchant_id);