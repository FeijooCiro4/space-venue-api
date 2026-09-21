# Backend de Space & Venue

Esta carpeta es un proyecto Maven independiente. Se puede copiar a otro repositorio con su `pom.xml`, `src/`, wrapper y Dockerfile.

Requisitos: Java 17 o superior y MySQL. La configuración del proceso se ejemplifica en `.env.example`. Exportá esas variables desde el IDE o la terminal; no se carga `.env` de forma implícita.

```bash
./mvnw test
./mvnw package
./mvnw spring-boot:run
```

Las pruebas usan H2 en memoria con el perfil `test`, que solo existe en `src/test/resources`.

```bash
docker build -t spacevenue-api .
# .env debe contener variables del backend, incluidas las de conexión a MySQL.
docker run --rm --env-file .env -p 8080:8080 spacevenue-api
```

En Docker, `localhost` en la URL JDBC se refiere al contenedor. Usá el nombre del contenedor MySQL en una red compartida o un host accesible desde Docker. El Compose de la raíz ya configura esa conexión.

Rutas disponibles al arrancar:

- `/api/...`: operaciones del negocio.
- `/v3/api-docs`: contrato OpenAPI generado por los controladores.
- `/swagger-ui/index.html`: documentación interactiva de la API.

## Variables

| Variable | Uso |
| --- | --- |
| `PORT` | Puerto interno; 8080 por defecto |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Conexión MySQL |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos, separados por comas, con puerto y sin barra final |
| `MP_ACCESS_TOKEN` | Token privado de Mercado Pago; nombre unificado en Compose y Render |
| `PAYMENT_SUCCESS_URL`, `PAYMENT_FAILURE_URL`, `PAYMENT_PENDING_URL` | Destinos completos de retorno al terminar el pago |

Solo el servidor necesita secretos de Mercado Pago y MySQL. Los destinos de retorno son configuración de la integración de pagos.

Los destinos de retorno están vacíos por defecto. Configurá los tres destinos públicos HTTPS aceptados por Mercado Pago antes de habilitar pagos. La configuración del webhook y sus pendientes se describen en `../docs/ARCHITECTURE.md`.

## Render

El `render.yaml` de la raíz usa `backend/Dockerfile` y el contexto `backend/`. Si extraés esta carpeta como repositorio independiente, usá `Dockerfile` y contexto `.`. Configurá las variables en el servicio de Render y los nuevos dominios en CORS y retornos.

Referencia de los campos de contexto: [Blueprint de Render](https://render.com/docs/blueprint-spec#docker).
