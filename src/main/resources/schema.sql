CREATE TABLE IF NOT EXISTS users (
    email         TEXT PRIMARY KEY,
    password_hash TEXT NOT NULL,
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS applications (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    TEXT    NOT NULL,
    company    TEXT    NOT NULL,
    title      TEXT    NOT NULL,
    url        TEXT,
    status     TEXT    NOT NULL DEFAULT 'found'
                       CHECK (status IN ('found', 'applied', 'blocked')),
    created_at TEXT    NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_applications_user_status ON applications (user_id, status);

CREATE TABLE IF NOT EXISTS criteria (
    user_id    TEXT PRIMARY KEY,
    text       TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
