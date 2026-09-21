# Backend de Space & Venue

Esta carpeta es un proyecto Maven independiente. Se puede copiar a otro repositorio con su `pom.xml`, `src/` y wrapper de Maven.

Requisitos: Java 17 o superior y MySQL. La configuración del proceso se ejemplifica en `.env.example`. Exportá esas variables desde el IDE o la terminal; no se carga `.env` de forma implícita.

```bash
./mvnw test
./mvnw package
./mvnw spring-boot:run
```

Las pruebas usan H2 en memoria con el perfil `test`, que solo existe en `src/test/resources`.

Para ejecutar el JAR compilado:

```bash
java -jar target/space-venueapi-0.0.1-SNAPSHOT.jar
```

MySQL debe estar disponible en la dirección indicada por `SPRING_DATASOURCE_URL`. El ejemplo usa `localhost:3306`; configurá el usuario y la contraseña de tu instancia.

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
| `MP_ACCESS_TOKEN` | Token privado de Mercado Pago |
| `PAYMENT_SUCCESS_URL`, `PAYMENT_FAILURE_URL`, `PAYMENT_PENDING_URL` | Destinos completos de retorno al terminar el pago |

Solo el servidor necesita secretos de Mercado Pago y MySQL. Los destinos de retorno son configuración de la integración de pagos.

Los destinos de retorno están vacíos por defecto. Configurá los tres destinos públicos HTTPS aceptados por Mercado Pago antes de habilitar pagos. La configuración del webhook y sus pendientes se describen en `../docs/ARCHITECTURE.md`.
