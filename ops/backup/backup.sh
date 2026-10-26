#!/bin/sh
# Taegliche Datenbanksicherung fuer Logis.
#   backup.sh         Dauerbetrieb: taeglich zur Uhrzeit BACKUP_TIME sichern
#   backup.sh --now   sofort eine Sicherung erstellen und beenden
# Erwartet PGHOST, PGUSER, PGPASSWORD, PGDATABASE (Standardvariablen von libpq).
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
    log "Sicherung erstellt: $(basename "$target") ($(du -h "$target" | cut -f1))"
  else
    rm -f "$tmp"
    log "FEHLER: Sicherung fehlgeschlagen"
    return 1
  fi
  # Aelter als die Aufbewahrungsdauer (in Minuten gerechnet, damit "14 Tage" exakt gilt).
  find "$BACKUP_DIR" -maxdepth 1 -type f -name 'logis_*.dump' \
    -mmin +$((BACKUP_RETENTION_DAYS * 24 * 60)) -print -delete | while read -r f; do
    log "Alte Sicherung geloescht: $(basename "$f")"
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

log "Gestartet: taeglich um $BACKUP_TIME, Aufbewahrung $BACKUP_RETENTION_DAYS Tage, Ziel $BACKUP_DIR"
while true; do
  wait=$(seconds_until_next_run)
  log "Naechste Sicherung in $((wait / 60)) Minuten"
  sleep "$wait"
  run_backup || true
done
