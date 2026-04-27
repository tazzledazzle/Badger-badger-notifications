CREATE TABLE templates (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    current_version INT NOT NULL DEFAULT 1
);

CREATE TABLE template_versions (
    id VARCHAR(36) PRIMARY KEY,
    template_id VARCHAR(36) NOT NULL REFERENCES templates (id) ON DELETE CASCADE,
    version INT NOT NULL,
    body TEXT NOT NULL,
    variant_tag VARCHAR(64) NULL,
    UNIQUE (template_id, version)
);

CREATE INDEX idx_templates_tenant ON templates (tenant_id);

CREATE TABLE user_preferences (
    user_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(36) NOT NULL,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    dnd_start_time TIME NULL,
    dnd_end_time TIME NULL,
    timezone VARCHAR(64) NULL,
    PRIMARY KEY (user_id, tenant_id)
);
