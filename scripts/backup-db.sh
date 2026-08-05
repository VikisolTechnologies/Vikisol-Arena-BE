#!/usr/bin/env bash
# PRODUCTION-CHECKLIST.md: "Automated daily DB backups and a tested restore drill (an untested
# backup is not a backup)." Plain pg_dump to a local/mounted path - PRODUCTION-CHECKLIST.md
# explicitly warns against relying solely on Railway's own dashboard-dependent backups, so this
# is meant to run independently (a Railway cron job / GitHub Actions schedule once either exists
# - see BLOCKED.md for why neither is wired up yet) and its output shipped to external object
# storage. Custom format (-Fc) rather than plain SQL so pg_restore can do parallel restores and
# selective table restores later, not just a single serial psql replay.
#
# Usage: DB_URL=postgresql://user:pass@host:port/dbname ./scripts/backup-db.sh [output-dir]
set -euo pipefail

OUT_DIR="${1:-./backups}"
mkdir -p "$OUT_DIR"

# Discrete -h/-p/-U flags + PGPASSWORD, not a postgresql:// URL - a password containing
# characters like `#` or `@` breaks URI parsing (a `#` is read as a fragment delimiter,
# truncating everything after it) unless percent-encoded, which is easy to get wrong. If DB_URL
# is already set (Railway's DATABASE_URL, pre-encoded), pg_dump can take that directly instead.
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-Welcome@12345#}"
DB_NAME="${DB_NAME:-vikisol_arena}"
export PGPASSWORD="$DB_PASSWORD"

TIMESTAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT_FILE="$OUT_DIR/arena-${TIMESTAMP}.dump"

PG_DUMP="${PG_DUMP_BIN:-pg_dump}"
if ! command -v "$PG_DUMP" >/dev/null 2>&1; then
  # Windows dev-box fallback - not present on Railway's Linux build image, where `pg_dump` is
  # already on PATH via the postgresql-client apt package installed in the Dockerfile.
  PG_DUMP="/c/Program Files/PostgreSQL/16/bin/pg_dump.exe"
fi

echo "Backing up $DB_NAME to $OUT_FILE ..."
if [ -n "${DB_URL:-}" ]; then
  "$PG_DUMP" --format=custom --no-owner --no-privileges --dbname="$DB_URL" --file="$OUT_FILE"
else
  "$PG_DUMP" --format=custom --no-owner --no-privileges -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$DB_NAME" --file="$OUT_FILE"
fi
echo "Backup complete: $(du -h "$OUT_FILE" | cut -f1) -> $OUT_FILE"

# Keep the last 14 daily backups locally; external object storage (once a bucket/credentials
# exist - see BLOCKED.md) should hold the real retention window, this is just a local safety net.
find "$OUT_DIR" -name "arena-*.dump" -mtime +14 -delete 2>/dev/null || true
