CREATE TABLE IF NOT EXISTS users (
    email         TEXT PRIMARY KEY,
    password_hash TEXT NOT NULL,
    created_at    TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS user_agents (
    slug       TEXT PRIMARY KEY,
    email      TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
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

-- One row per posting per account. The agent checks before it adds, but two
-- runs can interleave, so the database enforces it. Rows with no URL are left
-- alone (SQLite treats NULLs as distinct), and the oldest duplicate is kept.
DELETE FROM applications
 WHERE url IS NOT NULL
   AND id NOT IN (SELECT MIN(id) FROM applications WHERE url IS NOT NULL GROUP BY user_id, url);

CREATE UNIQUE INDEX IF NOT EXISTS idx_applications_user_url ON applications (user_id, url);
