ALTER TABLE model_asset MODIFY secret_ref VARCHAR(255) NULL;
ALTER TABLE model_asset ADD COLUMN api_key_ciphertext TEXT NULL AFTER secret_ref;
