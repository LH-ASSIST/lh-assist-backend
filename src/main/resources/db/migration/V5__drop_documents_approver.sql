ALTER TABLE documents
    DROP COLUMN IF EXISTS approver_id;

ALTER TABLE documents
    DROP CONSTRAINT IF EXISTS fk_documents_approver;