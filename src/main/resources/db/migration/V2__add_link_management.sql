-- Rows created before V2 have no owner or token hash, so they cannot be deactivated through the API.
ALTER TABLE urls
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN redirect_type VARCHAR(16) NOT NULL DEFAULT 'TEMPORARY',
    ADD COLUMN owner_id UUID NULL,
    ADD COLUMN management_token_hash VARCHAR(64) NULL,
    ADD CONSTRAINT urls_redirect_type_check CHECK (redirect_type IN ('TEMPORARY', 'PERMANENT')),
    ADD CONSTRAINT urls_click_count_check CHECK (click_count >= 0);
