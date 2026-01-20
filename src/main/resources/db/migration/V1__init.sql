CREATE EXTENSION IF NOT EXISTS vector;

CREATE TYPE doc_type_enum AS ENUM ('PLAN', 'NOTICE', 'CONTRACT', 'ETC');
CREATE TYPE analysis_status_enum AS ENUM ('PENDING', 'ANALYZING', 'COMPLETED', 'FAILED');
CREATE TYPE approval_status_enum AS ENUM ('WAITING', 'APPROVED', 'URGENT', 'REJECTED');
CREATE TYPE metadata_status_enum AS ENUM ('PENDING', 'COMPLETED', 'FAILED');

CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255),
    name VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    position VARCHAR(20) NOT NULL,
    department VARCHAR(50) NOT NULL,
    email_verified BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE regulations (
    reg_id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    reg_type VARCHAR(30) NOT NULL,
    effective_date DATE NOT NULL,
    expiry_date DATE,
    is_active BOOLEAN NOT NULL,
    amendment_date DATE NOT NULL,
    version VARCHAR(100) NOT NULL,
    source_url VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE reg_items (
    item_id BIGSERIAL PRIMARY KEY,
    clause_number VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    change_type VARCHAR(30) NOT NULL,
    is_mandatory BOOLEAN NOT NULL,
    vector_index VECTOR,
    parent_id BIGINT,
    reg_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_reg_items_parent FOREIGN KEY (parent_id) REFERENCES reg_items(item_id),
    CONSTRAINT fk_reg_items_regulation FOREIGN KEY (reg_id) REFERENCES regulations(reg_id)
);

CREATE TABLE audit_logs (
    log_id BIGSERIAL PRIMARY KEY,
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NOT NULL,
    s3_key VARCHAR(500) NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE documents (
    doc_id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    doc_type doc_type_enum,
    s3_key VARCHAR(500) NOT NULL,
    base_date DATE NOT NULL,
    analysis_status analysis_status_enum,
    approval_status approval_status_enum,
    metadata_status metadata_status_enum,
    user_id BIGINT NOT NULL,
    approver_id BIGINT,
    version BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_documents_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_documents_approver FOREIGN KEY (approver_id) REFERENCES users(user_id)
);
