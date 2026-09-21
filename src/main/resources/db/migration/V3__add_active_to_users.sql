-- New accounts start INACTIVE by default.
-- IF NOT EXISTS keeps this safe on databases where the column already exists;
-- the explicit ALTER covers those databases, which were created with DEFAULT TRUE.
ALTER TABLE users ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ALTER COLUMN active SET DEFAULT FALSE;
