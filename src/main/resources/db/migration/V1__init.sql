-- Initial schema for lh-assist-backend
CREATE EXTENSION IF NOT EXISTS vector;

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
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE documents (
    doc_id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    doc_type VARCHAR(255),
    s3_key VARCHAR(500) NOT NULL,
    base_date DATE NOT NULL,
    analysis_status VARCHAR(255),
    approval_status VARCHAR(255),
    metadata_status VARCHAR(255),
    user_id BIGINT NOT NULL,
    approver_id BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_documents_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_documents_approver FOREIGN KEY (approver_id) REFERENCES users(user_id)
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
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE reg_items (
    item_id BIGSERIAL PRIMARY KEY,
    clause_number VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    chunk_index INTEGER,
    page_number INTEGER,
    section_title VARCHAR(200),
    content_hash VARCHAR(64) NOT NULL,
    change_type VARCHAR(30) NOT NULL,
    is_mandatory BOOLEAN NOT NULL,
    embedding_model VARCHAR(100),
    vector_index vector,
    parent_id BIGINT,
    reg_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_reg_items_parent FOREIGN KEY (parent_id) REFERENCES reg_items(item_id),
    CONSTRAINT fk_reg_items_regulation FOREIGN KEY (reg_id) REFERENCES regulations(reg_id)
);

CREATE TABLE analysis_results (
    analysis_id BIGSERIAL PRIMARY KEY,
    doc_id BIGINT NOT NULL,
    base_date DATE NOT NULL,
    total_risk_score INTEGER,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_analysis_results_document FOREIGN KEY (doc_id) REFERENCES documents(doc_id)
);

CREATE TABLE analysis_jobs (
    job_id BIGSERIAL PRIMARY KEY,
    analysis_id BIGINT NOT NULL,
    doc_id BIGINT NOT NULL,
    base_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    fail_reason TEXT,
    retry_count INTEGER NOT NULL,
    requested_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_analysis_jobs_analysis_result FOREIGN KEY (analysis_id) REFERENCES analysis_results(analysis_id),
    CONSTRAINT fk_analysis_jobs_document FOREIGN KEY (doc_id) REFERENCES documents(doc_id),
    CONSTRAINT fk_analysis_jobs_user FOREIGN KEY (requested_by) REFERENCES users(user_id)
);

CREATE TABLE suggestions (
    suggestion_id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    is_private BOOLEAN NOT NULL,
    answer_content TEXT,
    answered_at TIMESTAMP,
    view_count INTEGER NOT NULL,
    is_anonymous BOOLEAN NOT NULL,
    user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_suggestions_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE notices (
    notice_id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    view_count INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE chat_messages (
    message_id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(100) NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    rag_references JSONB,
    item_id BIGINT,
    user_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_chat_messages_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE audit_logs (
    log_id BIGSERIAL PRIMARY KEY,
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NOT NULL,
    s3_key VARCHAR(500) NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE email_verifications (
    verify_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    attempt_count INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
