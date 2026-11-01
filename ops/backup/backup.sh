#!/bin/sh
# Daily database backup for Logis.
#   backup.sh         continuous mode: back up daily at BACKUP_TIME
#   backup.sh --now   create a backup immediately and exit
# Expects PGHOST, PGUSER, PGPASSWORD, PGDATABASE (standard libpq variables).
set -eu

BACKUP_DIR=${BACKUP_DIR:-/backups}
BACKUP_TIME=${BACKUP_TIME:-03:00}
BACKUP_RETENTION_DAYS=${BACKUP_RETENTION_DAYS:-14}

log() {
  echo "$(date '+%Y-%m-%d %H:%M:%S') backup: $*"
}

run_backup() {
  ts=$(date +%Y%m%d_%H%M%S)
  tmp="$BACKUP_DIR/.logis_$ts.dump.partial"
  target="$BACKUP_DIR/logis_$ts.dump"
  if pg_dump -Fc -f "$tmp"; then
    mv "$tmp" "$target"
    log "Backup created: $(basename "$target") ($(du -h "$target" | cut -f1))"
  else
    rm -f "$tmp"
    log "ERROR: backup failed"
    return 1
  fi
  # Older than the retention period (counted in minutes, so that "14 days" is exact).
  find "$BACKUP_DIR" -maxdepth 1 -type f -name 'logis_*.dump' \
    -mmin +$((BACKUP_RETENTION_DAYS * 24 * 60)) -print -delete | while read -r f; do
    log "Old backup deleted: $(basename "$f")"
  done
}

seconds_until_next_run() {
  now=$(date +%s)
  next=$(date -d "today $BACKUP_TIME" +%s)
  if [ "$next" -le "$now" ]; then
    next=$(date -d "tomorrow $BACKUP_TIME" +%s)
  fi
  echo $((next - now))
}

mkdir -p "$BACKUP_DIR"

if [ "${1:-}" = "--now" ]; then
  run_backup
  exit $?
fi

log "Started: daily at $BACKUP_TIME, retention $BACKUP_RETENTION_DAYS days, target $BACKUP_DIR"
while true; do
  wait=$(seconds_until_next_run)
  log "Next backup in $((wait / 60)) minutes"
  sleep "$wait"
  run_backup || true
done
