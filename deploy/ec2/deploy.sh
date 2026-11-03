# Script que corre como root en la EC2, enviado por GitHub Actions vía SSM.
# El workflow le antepone JAR_S3_URI, APP_SERVICE y APP_DIR.
# Es POSIX sh a propósito: AWS-RunShellScript lo ejecuta con /bin/sh.
set -eu

# Lee PORT del EnvironmentFile que declara el propio servicio (si no hay, usa 8080).
ENV_FILE=$(systemctl show -p EnvironmentFiles --value "$APP_SERVICE" | awk 'NR==1 {print $1}')
PORT=$(grep -E '^PORT=' "${ENV_FILE:-/dev/null}" 2>/dev/null | tail -n1 | cut -d= -f2 || true)
HEALTH_URL="http://localhost:${PORT:-8080}/v3/api-docs"

echo "Descargando $JAR_S3_URI"
aws s3 cp "$JAR_S3_URI" "$APP_DIR/app-new.jar" --only-show-errors
chmod 644 "$APP_DIR/app-new.jar"

if [ -f "$APP_DIR/app.jar" ]; then
  cp -p "$APP_DIR/app.jar" "$APP_DIR/app-prev.jar"
fi
mv "$APP_DIR/app-new.jar" "$APP_DIR/app.jar"

echo "Reiniciando $APP_SERVICE"
systemctl restart "$APP_SERVICE"

wait_healthy() {
  i=0
  while [ "$i" -lt 36 ]; do # hasta ~3 minutos
    if curl -fsS -o /dev/null "$HEALTH_URL"; then
      return 0
    fi
    i=$((i + 1))
    sleep 5
  done
  return 1
}

if wait_healthy; then
  echo "OK: $APP_SERVICE responde en $HEALTH_URL"
  exit 0
fi

echo "ERROR: la nueva versión no respondió. Últimos logs:" >&2
journalctl -u "$APP_SERVICE" -n 80 --no-pager >&2 || true

if [ -f "$APP_DIR/app-prev.jar" ]; then
  echo "Volviendo a la versión anterior" >&2
  cp -p "$APP_DIR/app-prev.jar" "$APP_DIR/app.jar"
  systemctl restart "$APP_SERVICE"
  if wait_healthy; then
    echo "Rollback OK: sigue corriendo la versión anterior" >&2
  else
    echo "Rollback FALLÓ: revisar la instancia" >&2
  fi
fi
exit 1
