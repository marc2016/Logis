#!/bin/sh
# Erzeugt set_real_ip_from-Zeilen aus LOGIS_TRUSTED_PROXIES (komma-getrennt).
# Nur von diesen Adressen wird X-Forwarded-For als echte Client-IP uebernommen.
set -eu

conf=/etc/nginx/conf.d/00-real-ip.conf
: > "$conf"
echo "${LOGIS_TRUSTED_PROXIES:-}" | tr ',' '\n' | while read -r proxy; do
  proxy=$(echo "$proxy" | tr -d ' ')
  [ -n "$proxy" ] && echo "set_real_ip_from $proxy;" >> "$conf"
done
cat >> "$conf" <<'CONF'
real_ip_header X-Forwarded-For;
real_ip_recursive on;
CONF
echo "logis: vertrauenswuerdige Proxys: ${LOGIS_TRUSTED_PROXIES:-<keine>}"
