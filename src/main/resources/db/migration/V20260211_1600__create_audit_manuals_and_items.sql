CREATE TABLE audit_manuals (
    manual_id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    code VARCHAR(50) UNIQUE,
    version VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE audit_manual_items (
    manual_item_id BIGSERIAL PRIMARY KEY,
    manual_id BIGINT NOT NULL,
    article_name VARCHAR(100),
    section_number INTEGER,
    content_type VARCHAR(30),
    content TEXT NOT NULL,
    vector_index vector(1536),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_audit_manual_items_manual
        FOREIGN KEY (manual_id) REFERENCES audit_manuals (manual_id),
    CONSTRAINT uk_manual_item
        UNIQUE (manual_id, article_name, section_number, content_type)
);

CREATE INDEX idx_audit_manual_items_manual_id
    ON audit_manual_items (manual_id);
