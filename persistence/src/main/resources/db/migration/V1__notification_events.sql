CREATE TABLE notification_events (
    event_id VARCHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(512) NULL,
    tenant_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    notification_kind VARCHAR(32) NOT NULL,
    template_id VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    tries INT NOT NULL DEFAULT 0,
    last_error TEXT NULL,
    variant_tag VARCHAR(64) NULL,
    fallback_channel VARCHAR(16) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_notification_events_idempotency
    ON notification_events (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_notification_events_tenant ON notification_events (tenant_id);
CREATE INDEX idx_notification_events_status ON notification_events (status);
