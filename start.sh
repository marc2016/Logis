#!/usr/bin/env bash
# Start Logis.
#   ./start.sh          development: Postgres (Docker) + backend (dev profile) + frontend (Vite)
#   ./start.sh prod     production: full Docker stack from compose.yml with .env
#   ./start.sh stop     stop the development database
set -euo pipefail

cd "$(dirname "$0")"

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mError:\033[0m %s\n' "$*" >&2; exit 1; }

require() {
  command -v "$1" >/dev/null 2>&1 || fail "'$1' was not found. $2"
}

# Prefix a process's output line by line.
prefix() {
  awk -v p="$1" '{ print p " " $0; fflush() }'
}

port_in_use() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

start_dev() {
  require docker "Please install or start Docker."
  require java "Please install JDK 21 or newer."
  require npm "Please install Node.js 22 or newer."
  for port in 8080 5173; do
    port_in_use "$port" && fail "Port $port is already in use (is Logis already running?): $(lsof -nP -iTCP:$port -sTCP:LISTEN | awk 'NR==2 {print $1, "PID", $2}')"
  done

  info "Starting development database ..."
  docker compose -f compose.dev.yml up -d --wait >/dev/null

  if [ ! -d frontend/node_modules ]; then
    info "Installing frontend dependencies ..."
    (cd frontend && npm install)
  fi

  # Each background process gets its own process group, which is stopped specifically on exit.
  set -m
  backend_pid=""
  frontend_pid=""
  cleanup() {
    trap - INT TERM EXIT
    echo
    info "Stopping backend and frontend ..."
    for pid in $backend_pid $frontend_pid; do
      kill -- -"$pid" 2>/dev/null || true
    done
    wait 2>/dev/null || true
  }
  trap cleanup INT TERM EXIT

  info "Starting backend (dev profile) ..."
  (cd backend && ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=dev 2>&1 | prefix $'\033[32m[backend]\033[0m') &
  backend_pid=$!

  info "Starting frontend (Vite) ..."
  (cd frontend && npm run dev -- --strictPort 2>&1 | prefix $'\033[35m[frontend]\033[0m') &
  frontend_pid=$!

  info "Waiting for the backend ..."
  for _ in $(seq 1 90); do
    kill -0 "$backend_pid" 2>/dev/null || fail "The backend exited - see output above."
    if curl -fs http://localhost:8080/api/health >/dev/null 2>&1; then
      echo
      info "Logis is running:  http://localhost:5173"
      info "Sign-in:           admin / admin-dev-password  (set up 2FA at first sign-in)"
      info "Stop with Ctrl+C. The database keeps running (./start.sh stop stops it)."
      echo
      wait
      return
    fi
    sleep 2
  done
  fail "The backend is not reachable after 3 minutes - see output above."
}

start_prod() {
  require docker "Please install or start Docker."
  [ -f .env ] || fail ".env is missing. Copy the template: cp .env.example .env - and adjust the values."
  if grep -q 'CHANGE_ME' .env; then
    fail ".env still contains placeholders (CHANGE_ME): $(grep -o '^[A-Z_]*=CHANGE_ME' .env | cut -d= -f1 | tr '\n' ' ')"
  fi

  info "Building and starting the stack ..."
  docker compose up -d --build --wait
  docker compose ps --format 'table {{.Service}}\t{{.Status}}'

  bind=$(grep -E '^LOGIS_BIND=' .env | cut -d= -f2-); port=$(grep -E '^LOGIS_PORT=' .env | cut -d= -f2-)
  info "Logis is running on http://${bind:-127.0.0.1}:${port:-8080} - reachable through the reverse proxy."
}

case "${1:-dev}" in
  dev) start_dev ;;
  prod) start_prod ;;
  stop)
    info "Stopping development database ..."
    docker compose -f compose.dev.yml stop
    ;;
  -h | --help | help)
    sed -n '2,5p' "$0" | sed 's/^# \{0,1\}//'
    ;;
  *) fail "Unknown command '$1'. Available: dev (default), prod, stop" ;;
esac
