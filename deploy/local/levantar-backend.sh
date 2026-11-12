#!/usr/bin/env bash
# Levanta la base local (Docker) y el backend en http://localhost:8080 apuntando a ella.
# Uso: ./deploy/local/levantar-backend.sh   (Ctrl+C para frenar el backend; la base sigue corriendo)
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

docker compose -f "$DIR/docker-compose.yml" up -d --wait

# Clave JWT estable entre reinicios (si cambia, los tokens guardados en el navegador dejan de valer)
if [ ! -f "$DIR/.env" ]; then
  echo "JWT_SECRET_BASE64=$(openssl rand -base64 32)" > "$DIR/.env"
fi
set -a; source "$DIR/.env"; set +a

export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3307/space_venue_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
export SPRING_DATASOURCE_USERNAME=spacevenue
export SPRING_DATASOURCE_PASSWORD=spacevenue-local
export CORS_ALLOWED_ORIGINS="http://localhost:4200,http://127.0.0.1:4200"
export PORT=8080
# Sin AWS ni Mercado Pago: la subida de imágenes y los pagos no funcionan en local
export AWS_S3_BUCKET=""
export MP_ACCESS_TOKEN=""

# Hibernate crea las tablas al arrancar: los datos iniciales se cargan cuando ya existen
(
  for _ in $(seq 1 120); do
    if docker exec space-venue-mysql-local mysql -uspacevenue -pspacevenue-local space_venue_db \
         -e "SELECT 1 FROM cancellationpolicies LIMIT 1" >/dev/null 2>&1; then
      docker exec -i space-venue-mysql-local mysql -uspacevenue -pspacevenue-local space_venue_db \
        < "$DIR/datos-iniciales.sql" 2>/dev/null && echo ">> Datos iniciales cargados en la base local"
      exit 0
    fi
    sleep 2
  done
) &

cd "$DIR/../../backend"
exec ./mvnw spring-boot:run
