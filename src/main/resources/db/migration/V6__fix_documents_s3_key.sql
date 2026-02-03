DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'documents'
          AND column_name = 's3key'
    ) THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = 'documents'
              AND column_name = 's3_key'
        ) THEN
            ALTER TABLE documents DROP COLUMN s3key;
        ELSE
            ALTER TABLE documents RENAME COLUMN s3key TO s3_key;
        END IF;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'documents'
          AND column_name = 's3_key'
    ) THEN
        ALTER TABLE documents ALTER COLUMN s3_key SET NOT NULL;
    END IF;
END $$;