CREATE TABLE audit_items (
    item_id BIGSERIAL PRIMARY KEY,
    doc_id VARCHAR(100) NOT NULL,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    doc_title VARCHAR(200),
    source_path VARCHAR(500),
    embedding_model VARCHAR(100),
    vector_index vector(1536),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_audit_doc_chunk UNIQUE (doc_id, chunk_index)
);

ALTER TABLE reg_items
    ALTER COLUMN vector_index TYPE vector(1536);