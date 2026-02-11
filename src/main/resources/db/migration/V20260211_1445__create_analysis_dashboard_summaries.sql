CREATE TABLE IF NOT EXISTS analysis_dashboard_summaries (
    summary_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    year_month VARCHAR(7) NOT NULL,
    monthly_review_count BIGINT NOT NULL,
    high_risk_document_count BIGINT NOT NULL,
    average_safety_score INTEGER NOT NULL,
    low_risk_document_count BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_analysis_dashboard_summaries_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT uq_analysis_dashboard_summaries_user_month
        UNIQUE (user_id, year_month)
);

CREATE INDEX IF NOT EXISTS idx_analysis_dashboard_summaries_user_id
    ON analysis_dashboard_summaries (user_id);
