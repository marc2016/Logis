#!/usr/bin/env bash
# Logis starten.
#   ./start.sh          Entwicklung: Postgres (Docker) + Backend (Profil dev) + Frontend (Vite)
#   ./start.sh prod     Produktion: kompletter Docker-Stack aus compose.yml mit .env
#   ./start.sh stop     Entwicklungs-Datenbank anhalten
set -euo pipefail

cd "$(dirname "$0")"

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mFehler:\033[0m %s\n' "$*" >&2; exit 1; }

require() {
  command -v "$1" >/dev/null 2>&1 || fail "'$1' wurde nicht gefunden. $2"
}

# Ausgabe eines Prozesses zeilenweise mit Praefix versehen.
prefix() {
  awk -v p="$1" '{ print p " " $0; fflush() }'
}

port_in_use() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

start_dev() {
  require docker "Bitte Docker installieren bzw. starten."
  require java "Bitte ein JDK 21 oder neuer installieren."
  require npm "Bitte Node.js 22 oder neuer installieren."
  for port in 8080 5173; do
    port_in_use "$port" && fail "Port $port ist bereits belegt (laeuft Logis schon?): $(lsof -nP -iTCP:$port -sTCP:LISTEN | awk 'NR==2 {print $1, "PID", $2}')"
  done

  info "Starte Entwicklungs-Datenbank ..."
  docker compose -f compose.dev.yml up -d --wait >/dev/null

  if [ ! -d frontend/node_modules ]; then
    info "Installiere Frontend-Abhaengigkeiten ..."
    (cd frontend && npm install)
  fi

  # Jeder Hintergrundprozess bekommt eine eigene Prozessgruppe, die beim Beenden gezielt gestoppt wird.
  set -m
  backend_pid=""
  frontend_pid=""
  cleanup() {
    trap - INT TERM EXIT
    echo
    info "Beende Backend und Frontend ..."
    for pid in $backend_pid $frontend_pid; do
      kill -- -"$pid" 2>/dev/null || true
    done
    wait 2>/dev/null || true
  }
  trap cleanup INT TERM EXIT

  info "Starte Backend (Profil dev) ..."
  (cd backend && ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | prefix $'\033[32m[backend]\033[0m') &
  backend_pid=$!

  info "Starte Frontend (Vite) ..."
  (cd frontend && npm run dev -- --strictPort 2>&1 | prefix $'\033[35m[frontend]\033[0m') &
  frontend_pid=$!

  info "Warte auf das Backend ..."
  for _ in $(seq 1 90); do
    kill -0 "$backend_pid" 2>/dev/null || fail "Das Backend wurde beendet - siehe Ausgabe oben."
    if curl -fs http://localhost:8080/api/health >/dev/null 2>&1; then
      echo
      info "Logis laeuft:  http://localhost:5173"
      info "Anmeldung:     admin / admin-dev-password  (beim ersten Login 2FA einrichten)"
      info "Beenden mit Strg+C. Die Datenbank laeuft weiter (./start.sh stop haelt sie an)."
      echo
      wait
      return
    fi
    sleep 2
  done
  fail "Das Backend ist nach 3 Minuten nicht erreichbar - siehe Ausgabe oben."
}

start_prod() {
  require docker "Bitte Docker installieren bzw. starten."
  [ -f .env ] || fail ".env fehlt. Vorlage kopieren: cp .env.example .env - und Werte anpassen."
  if grep -q 'CHANGE_ME' .env; then
    fail ".env enthaelt noch Platzhalter (CHANGE_ME): $(grep -o '^[A-Z_]*=CHANGE_ME' .env | cut -d= -f1 | tr '\n' ' ')"
  fi

  info "Baue und starte den Stack ..."
  docker compose up -d --build --wait
  docker compose ps --format 'table {{.Service}}\t{{.Status}}'

  bind=$(grep -E '^LOGIS_BIND=' .env | cut -d= -f2-); port=$(grep -E '^LOGIS_PORT=' .env | cut -d= -f2-)
  info "Logis laeuft auf http://${bind:-127.0.0.1}:${port:-8080} - erreichbar ueber den Reverse Proxy."
}

case "${1:-dev}" in
  dev) start_dev ;;
  prod) start_prod ;;
  stop)
    info "Halte Entwicklungs-Datenbank an ..."
    docker compose -f compose.dev.yml stop
    ;;
  -h | --help | help)
    sed -n '2,5p' "$0" | sed 's/^# \{0,1\}//'
    ;;
  *) fail "Unbekannter Befehl '$1'. Verfuegbar: dev (Standard), prod, stop" ;;
esac
