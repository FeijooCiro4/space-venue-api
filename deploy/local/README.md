# Entorno de pruebas local

Base MySQL 8.4 en Docker (puerto 3307, sin datos de producción) + backend en `http://localhost:8080`
+ front en `http://localhost:4200` apuntando a ese backend.

## Levantar

```bash
# Terminal 1 — base + backend (desde space-venue-api)
./deploy/local/levantar-backend.sh

# Terminal 2 — front (desde space-venue-front)
npm run start:local
```

La primera vez, registrate desde el front (o con /api/auth/register) y convertí un usuario en admin:

```bash
./deploy/local/cambiar-rol.sh <username> ROLE_ADMIN
```

Después cerrá sesión y volvé a entrar (o recargá) para ver el menú de admin.

## Limitaciones

- No hay S3 ni Mercado Pago: subir imágenes y pagar reservas no funciona.
- Las políticas de cancelación (`datos-iniciales.sql`) son valores de ejemplo.
- Los errores `alter table if exists ... add constraint` al arrancar vienen de usar `MariaDBDialect`
  contra MySQL: no se crean las foreign keys, pero el backend funciona.

## Frenar / borrar

```bash
docker compose -f deploy/local/docker-compose.yml down       # frena la base (conserva los datos)
docker compose -f deploy/local/docker-compose.yml down -v    # frena y BORRA la base local
```
