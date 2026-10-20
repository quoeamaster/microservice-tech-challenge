-- Runs on every startup (spring.sql.init.mode=always); safe to re-run.
CREATE TABLE IF NOT EXISTS fetched_photos (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    animal_type    TEXT NOT NULL CHECK (animal_type IN ('CAT', 'DOG', 'BEAR')),
    image_binary   BLOB NOT NULL,
    content_type   TEXT NOT NULL,
    timestamp      TEXT NOT NULL,   -- ISO-8601 UTC, when the photo was fetched
    source_website TEXT NOT NULL    -- full URL the photo was downloaded from
);

CREATE INDEX IF NOT EXISTS idx_fetched_photos_animal ON fetched_photos (animal_type, id);
