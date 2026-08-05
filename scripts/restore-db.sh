#!/usr/bin/env bash
# Restores a backup produced by backup-db.sh. Defaults to restoring into a *separate* database
# (RESTORE_DB_NAME, "arena_restore_drill" by default) rather than the live one, so running this
# to verify a backup is actually restorable never risks the real data - point RESTORE_DB_NAME at
# the real db name explicitly for an actual disaster-recovery restore.
#
# Usage: ./scripts/restore-db.sh path/to/arena-TIMESTAMP.dump
set -euo pipefail

DUMP_FILE="${1:?Usage: restore-db.sh <dump-file>}"
[ -f "$DUMP_FILE" ] || { echo "No such file: $DUMP_FILE"; exit 1; }

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-Welcome@12345#}"
RESTORE_DB_NAME="${RESTORE_DB_NAME:-arena_restore_drill}"

PG_RESTORE="${PG_RESTORE_BIN:-pg_restore}"
PSQL="${PSQL_BIN:-psql}"
if ! command -v "$PG_RESTORE" >/dev/null 2>&1; then
  PG_RESTORE="/c/Program Files/PostgreSQL/16/bin/pg_restore.exe"
  PSQL="/c/Program Files/PostgreSQL/16/bin/psql.exe"
fi

export PGPASSWORD="$DB_PASSWORD"

echo "Recreating $RESTORE_DB_NAME on $DB_HOST:$DB_PORT ..."
"$PSQL" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d postgres -c "DROP DATABASE IF EXISTS $RESTORE_DB_NAME;"
"$PSQL" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d postgres -c "CREATE DATABASE $RESTORE_DB_NAME;"

echo "Restoring $DUMP_FILE into $RESTORE_DB_NAME ..."
"$PG_RESTORE" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$RESTORE_DB_NAME" --no-owner --no-privileges "$DUMP_FILE"

TABLE_COUNT=$("$PSQL" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$RESTORE_DB_NAME" -t -c "select count(*) from information_schema.tables where table_schema='public';")
ROW_COUNT=$("$PSQL" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USERNAME" -d "$RESTORE_DB_NAME" -t -c "select count(*) from arena_users;")
echo "Restore complete: $TABLE_COUNT tables, $ROW_COUNT rows in arena_users."
