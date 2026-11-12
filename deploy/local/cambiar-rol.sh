#!/usr/bin/env bash
# Cambia el rol de un usuario directamente en la base local.
# Uso: ./deploy/local/cambiar-rol.sh <username> <ROLE_ADMIN|ROLE_OFFERER|ROLE_CLIENT>
# Sirve para crear el primer admin (el registro siempre crea ROLE_CLIENT).
# Después hay que cerrar sesión y volver a entrar (o recargar la página) para ver el menú nuevo.
set -euo pipefail
if [ $# -ne 2 ]; then
  echo "Uso: $0 <username> <ROLE_ADMIN|ROLE_OFFERER|ROLE_CLIENT>" >&2; exit 1
fi
case "$2" in ROLE_ADMIN|ROLE_OFFERER|ROLE_CLIENT) ;; *) echo "Rol inválido: $2" >&2; exit 1;; esac
USUARIO="${1//\'/}"
docker exec space-venue-mysql-local mysql -uspacevenue -pspacevenue-local space_venue_db \
  -e "UPDATE credentials SET rol='$2' WHERE username='$USUARIO'; SELECT username, rol FROM credentials WHERE username='$USUARIO';" 2>/dev/null
