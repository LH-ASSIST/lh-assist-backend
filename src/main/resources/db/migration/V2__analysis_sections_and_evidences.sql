CREATE TABLE analysis_sections (
    section_id BIGSERIAL PRIMARY KEY,
    analysis_id BIGINT NOT NULL,
    external_section_id VARCHAR(255) NOT NULL,
    bbox TEXT,
    page_number INTEGER,
    is_violation BOOLEAN NOT NULL DEFAULT FALSE,
    risk_score INTEGER,
    reasoning TEXT,
    CONSTRAINT fk_analysis_sections_result FOREIGN KEY (analysis_id) REFERENCES analysis_results(analysis_id)
);

CREATE TABLE analysis_evidences (
    evidence_id BIGSERIAL PRIMARY KEY,
    section_id BIGINT NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_id VARCHAR(255),
    quote TEXT,
    CONSTRAINT fk_analysis_evidences_section FOREIGN KEY (section_id) REFERENCES analysis_sections(section_id)
);