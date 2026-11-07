#!/bin/sh
# Generates set_real_ip_from lines from LOGIS_TRUSTED_PROXIES (comma-separated).
# X-Forwarded-For is accepted as the real client IP only from these addresses.
set -eu

conf=/etc/nginx/conf.d/00-real-ip.conf
: > "$conf"
echo "${LOGIS_TRUSTED_PROXIES:-}" | tr ',' '\n' | while read -r proxy; do
  proxy=$(echo "$proxy" | tr -d ' ')
  # if instead of "&&": an empty last line must not become the loop's exit status (set -e).
  if [ -n "$proxy" ]; then
    echo "set_real_ip_from $proxy;" >> "$conf"
  fi
done
cat >> "$conf" <<'CONF'
real_ip_header X-Forwarded-For;
real_ip_recursive on;
CONF
echo "logis: trusted proxies: ${LOGIS_TRUSTED_PROXIES:-<none>}"
