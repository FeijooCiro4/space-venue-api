# Space & Venue API

Backend de reservas de espacios desarrollado con Java 17, Spring Boot 4, Maven y MySQL. Incluye autenticación JWT, gestión de usuarios y espacios, reservas, notificaciones y pagos con Mercado Pago.

```text
backend/
  pom.xml             Proyecto Maven
  mvnw / mvnw.cmd     Wrapper de Maven
  src/main/java/      Controladores, servicios, modelos y repositorios
  src/main/resources/ Configuración del servidor
  src/test/           Pruebas con H2 en memoria
  .env.example        Referencia de variables del proceso
docs/
  API.md              Contrato HTTP y ejemplos de peticiones
  ARCHITECTURE.md     Arquitectura y pendientes técnicos
```

## Requisitos y configuración local

Instalá Java 17 o superior y MySQL. Creá la base con un usuario que tenga los permisos necesarios:

```sql
CREATE DATABASE space_venue_db;
```

Configurá las variables de [backend/.env.example](backend/.env.example) en el IDE o exportalas en la terminal. El archivo es una referencia; Maven no carga `.env` automáticamente.

Ejemplo para Bash, reemplazando usuario y contraseña por los de tu instancia local:

```bash
export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/space_venue_db?serverTimezone=UTC'
export SPRING_DATASOURCE_USERNAME='spacevenue'
export SPRING_DATASOURCE_PASSWORD='tu-contraseña-local'
cd backend
./mvnw spring-boot:run
```

En Windows, usá `mvnw.cmd` y configurá las variables del proceso desde tu terminal o IDE. También podés abrir `backend/pom.xml` y ejecutar `com.utn.space.venueaapi.Application`.

La API escucha en `http://localhost:8080/api`. El contrato OpenAPI está en `http://localhost:8080/v3/api-docs` y la documentación interactiva en `http://localhost:8080/swagger-ui/index.html`.

Hibernate conserva la configuración actual `ddl-auto=update`. Usá una base de desarrollo para las pruebas manuales.

Mercado Pago es opcional para el arranque. Para habilitar pagos, configurá `MP_ACCESS_TOKEN` y los destinos completos `PAYMENT_SUCCESS_URL`, `PAYMENT_FAILURE_URL`, `PAYMENT_PENDING_URL`.

## Pruebas y compilación

Desde `backend/`:

```bash
./mvnw test
./mvnw package
java -jar target/space-venueapi-0.0.1-SNAPSHOT.jar
```

Las pruebas usan H2 en memoria y no requieren MySQL ni credenciales reales. Para ejecutar el JAR deben estar configuradas las variables de conexión a MySQL. También podés usar Maven instalado (`mvn test`).

## Documentación

- [Contrato de la API](docs/API.md).
- [Arquitectura y pendientes técnicos](docs/ARCHITECTURE.md).
- [Configuración del backend](backend/README.md).
