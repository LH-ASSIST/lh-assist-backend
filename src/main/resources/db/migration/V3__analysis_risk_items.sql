CREATE TABLE analysis_risk_items (
    risk_id BIGSERIAL PRIMARY KEY,
    section_id BIGINT NOT NULL,
    risk_type VARCHAR(30) NOT NULL,
    detected_text TEXT NOT NULL,
    guide_message TEXT NOT NULL,
    priority INTEGER NOT NULL,
    similar_case_content TEXT,
    reasoning TEXT NOT NULL,
    CONSTRAINT fk_analysis_risk_items_section FOREIGN KEY (section_id) REFERENCES analysis_sections(section_id)
);

ALTER TABLE analysis_evidences
    ADD COLUMN risk_id BIGINT;

ALTER TABLE analysis_evidences
    ADD CONSTRAINT fk_analysis_evidences_risk FOREIGN KEY (risk_id) REFERENCES analysis_risk_items(risk_id);