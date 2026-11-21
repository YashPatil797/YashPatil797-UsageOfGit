CREATE TABLE short_urls (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    short_code VARCHAR(50) NOT NULL UNIQUE,
    original_url TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    hit_count BIGINT NOT NULL DEFAULT 0 CHECK (hit_count >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    redirect_type VARCHAR(16) NOT NULL DEFAULT 'TEMPORARY'
        CHECK (redirect_type IN ('TEMPORARY', 'PERMANENT')),
    owner_id UUID NOT NULL,
    management_token_hash VARCHAR(64) NOT NULL
);
