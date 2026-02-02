CREATE TABLE document_approvals (
    approval_id BIGSERIAL PRIMARY KEY,
    doc_id BIGINT NOT NULL,
    approver_id BIGINT,
    reviewer_name VARCHAR(50),
    reviewer_title VARCHAR(50),
    reviewer_dept VARCHAR(100),
    status VARCHAR(30) NOT NULL,
    reviewed_at TIMESTAMP,
    review_comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_document_approvals_document FOREIGN KEY (doc_id) REFERENCES documents(doc_id),
    CONSTRAINT uk_document_approvals_doc UNIQUE (doc_id)
);