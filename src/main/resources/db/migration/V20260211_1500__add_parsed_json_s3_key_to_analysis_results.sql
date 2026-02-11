ALTER TABLE analysis_results
ADD COLUMN IF NOT EXISTS parsed_json_s3_key VARCHAR(500);
