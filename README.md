# Space & Venue API

Backend de reservas de espacios desarrollado con Java 17, Spring Boot 4, Maven y MySQL. Incluye autenticación JWT, gestión de usuarios y espacios, reservas, notificaciones y pagos con Mercado Pago.

```text
backend/
  pom.xml             Proyecto Maven
  src/main/java/      Controladores, servicios, modelos y repositorios
  src/main/resources/ Configuración del servidor
  src/test/           Pruebas con H2 en memoria
  Dockerfile          Imagen de la API
docs/
  API.md              Contrato HTTP y ejemplos de peticiones
  ARCHITECTURE.md     Arquitectura y pendientes técnicos
docker-compose.yml    API y MySQL para desarrollo local
render.yaml           Servicio del backend en Render
```

## Ejecutar con Docker

Desde la raíz:

```bash
cp .env.example .env
# Editar .env con la configuración del entorno.
docker compose up --build
```

API: `http://localhost:8080/api`. Contrato OpenAPI: `http://localhost:8080/v3/api-docs`. Documentación interactiva: `http://localhost:8080/swagger-ui/index.html`.

Mercado Pago es opcional para el arranque. Para habilitar pagos, configurá `MP_ACCESS_TOKEN` y los destinos completos `PAYMENT_SUCCESS_URL`, `PAYMENT_FAILURE_URL`, `PAYMENT_PENDING_URL`. No hay destinos de retorno predeterminados.

Si ya existe un volumen MySQL, usá sus credenciales actuales: las variables de inicialización no modifican usuarios de una base creada. Se conserva el volumen `db_data`.

## Ejecutar y probar Java

Abrí `backend/pom.xml` en el IDE. Exportá las variables de [backend/.env.example](backend/.env.example) para ejecutar el servidor; Maven no carga archivos `.env` automáticamente.

```bash
cd backend
./mvnw spring-boot:run
./mvnw test
./mvnw package
```

Las pruebas usan una base H2 en memoria y no requieren MySQL ni credenciales reales. También podés usar Maven instalado (`mvn test`). El JAR se genera en `backend/target/`.

## Documentación

- [Contrato de la API](docs/API.md).
- [Arquitectura y pendientes técnicos](docs/ARCHITECTURE.md).
- [Configuración del backend](backend/README.md).
