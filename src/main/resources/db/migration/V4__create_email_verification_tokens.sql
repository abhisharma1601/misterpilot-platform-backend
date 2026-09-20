-- Email verification tokens for the email/password signup flow.
-- Google signups skip this entirely and are activated immediately.
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id         BIGSERIAL    PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL
);

-- Lookups are always by token (verify) or email (invalidate/resend).
CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_token
    ON email_verification_tokens (token);

CREATE INDEX IF NOT EXISTS idx_email_verification_tokens_email
    ON email_verification_tokens (email);
