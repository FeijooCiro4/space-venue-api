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
| `JWT_SECRET_BASE64` | Obligatoria: clave privada de al menos 32 bytes aleatorios en Base64, estable y compartida entre instancias |
| `MP_ACCESS_TOKEN` | Token privado de Mercado Pago |
| `PAYMENT_SUCCESS_URL`, `PAYMENT_FAILURE_URL`, `PAYMENT_PENDING_URL` | Destinos completos de retorno al terminar el pago |

Solo el servidor necesita la clave JWT y los secretos de Mercado Pago y MySQL. Los destinos de retorno son configuración de la integración de pagos.

Los destinos de retorno están vacíos por defecto. Configurá los tres destinos públicos HTTPS aceptados por Mercado Pago antes de habilitar pagos. La configuración del webhook y sus pendientes se describen en `../docs/ARCHITECTURE.md`.

## Clave JWT y logout

Generá la clave **una sola vez** con `openssl rand -base64 32` y guardá el resultado como `JWT_SECRET_BASE64` en la configuración privada del proceso. Reutilizá exactamente ese valor después de reiniciar y en todas las instancias que compartan la base. No uses la clave de `application-test.properties` fuera de pruebas. La aplicación falla al arrancar si falta una clave válida.

Los tokens duran diez horas y cada emisión tiene un identificador único. El logout guarda una huella SHA-256 del token y su vencimiento en `revoked_tokens`; no guarda el token reutilizable. La revocación se consulta en la base compartida y sobrevive a reinicios. Una tarea elimina los registros vencidos cada hora.

Con `ddl-auto=update`, Hibernate crea la tabla `revoked_tokens` y su índice al arrancar. En despliegues con migraciones administradas debe incorporarse esa tabla antes de publicar. Al adoptar esta configuración, los tokens emitidos por la versión anterior requieren un nuevo login, porque aquella clave solo existía en memoria.


En criollo:

Que es: KWT_SECRET_BASE64
Es una contraseña maestra que usa el servidor para firmar los tokens JWT. Se genera con openssl rand -base64 32 en una terminal. Este resultado se guardara con las varaibles de entorno del servidor con el nombre JWT_SECRET_BASE64

revoked_tokens:
Existe para implementar correctamente los logouts del ususario que ocurran antes de las 10 horas que el JWT dura por default. En cada petición entrante, el servidor consulta la base de datos para comprobar si la huella del token está en la lista de revocados.