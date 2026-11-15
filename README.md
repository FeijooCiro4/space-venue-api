# Space&Venue - API REST Backend

## 1. Información del Sistema

- **Nombre**: Space&Venue (API Backend).
- **Tipo de sistema**: Servidor API RESTful sin estado (*stateless*).
- **Carácter del proyecto**: Proyecto Académico Integrador desarrollado para las asignaturas “Gestión de Desarrollo de Software” y “Programación IV” de la Universidad Tecnológica Nacional (UTN FRMDP), cursada 2026.
- **Descripción**: Plataforma web para conectar propietarios de espacios ociosos con clientes que buscan locaciones para eventos breves. El producto está orientado a usos de menos de 24 horas.
- **Problema que soluciona**: La baja monetización de espacios subutilizados y la falta de una oferta transparente para alquileres temporales sin contratos de larga estancia.
- **Visión y objetivos**: Proveer una API segura y mantenible para administrar usuarios, espacios, servicios, reservas, pagos y notificaciones, con reglas de disponibilidad y control de concurrencia.

El backend vive en `backend/` y se ejecuta como una aplicación Spring Boot independiente. La URL base local es `http://localhost:8080/api`.

## 2. Reglas de Negocio y Decisiones de Arquitectura

- **Duración del evento**: El requisito funcional del producto es que cada evento dure estrictamente menos de 24 horas y se desarrolle dentro de una misma jornada.
- **Bloqueo de autorreserva**: El cliente que solicita la reserva no puede ser el propietario del espacio; la comprobación se realiza en el servicio de reservas.
- **Disponibilidad y concurrencia**: Las reservas activas en estado `TENTATIVE`, `CONFIRMED`, `IN_PROGRESS` o `COMPLETED` ocupan el calendario. La creación y las operaciones que cambian disponibilidad bloquean las entidades necesarias para serializar solicitudes concurrentes. Las reservas `REJECTED` o `CANCELLED` no bloquean el horario.
- **Buffer time**: `bufferTime` se expresa en minutos y se aplica antes y después de una reserva al comprobar solapamientos, para evitar eventos consecutivos sin intervalo de preparación.
- **Tarifas de servicios**: Al seleccionar un servicio para una reserva, el backend copia la tarifa vigente a `priceAtReservation` y congela también su descripción. Los cambios posteriores del catálogo no modifican lo ya contratado. El precio total se calcula en servidor a partir de la tarifa horaria del espacio, la duración y los adicionales.
- **Flujo de reservas**: Una solicitud nueva queda en estado `TENTATIVE`; el oferente puede confirmarla o rechazarla. El checkout de Mercado Pago se habilita para reservas confirmadas. La aplicación registra pagos y reembolsos, pero una cancelación con reintegro pendiente no implica que el dinero se reembolse automáticamente (esto debido a que la implementación de Mercado Pago no forma parte del MVP del proyecto, por lo que no se tomará como funcionalidad dentro de la primera versión).
- **Arquitectura de seguridad**: Spring Security usa sesiones `STATELESS` y autenticación mediante JWT firmado con HMAC-SHA256 (`HS256`), con vigencia de 10 horas. `JWT_SECRET_BASE64` debe contener una clave Base64 estable de al menos 32 bytes aleatorios. El logout registra la huella del token y su vencimiento en `revoked_tokens`; la lista de revocación persiste en MySQL y se comparte entre instancias que usan la misma base y clave.
- **Contraseñas y privacidad**: Las contraseñas se almacenan con BCrypt. Los DTO públicos de espacios limitan los datos del propietario; las credenciales y los datos de contacto no forman parte del catálogo público.

## 3. Convenciones de Programación y Guía de Estilo (Spring Boot 2026)

- **DTO (Data Transfer Object)**: Los DTO desacoplan el contrato HTTP de las entidades JPA. Se utilizan registros y clases específicas para solicitudes, respuestas, filtros y errores; no se deben exponer entidades ni credenciales en nuevas rutas públicas.
- **Arquitectura en capas**: `Controller → Service → Repository → Database`. Los controladores traducen HTTP, los servicios aplican reglas y transacciones, los repositorios encapsulan persistencia y JPA administra el mapeo relacional.
- **Validación y errores**: Se usan Jakarta Validation y excepciones de dominio. `GlobalExceptionHandler`, anotado con `@RestControllerAdvice`, normaliza errores en `ErrorResponseDTO`; Spring Security también responde con estados 401/403. Los éxitos pueden ser JSON, texto plano o cuerpo vacío según la operación.
- **Autorización**: Se combina la configuración global de rutas con `@EnableMethodSecurity`, anotaciones `@PreAuthorize` y verificaciones de titularidad/propiedad. Las comprobaciones de permisos deben basarse en la identidad autenticada y en los datos persistidos, no en identificadores confiados del cuerpo.
- **Persistencia**: El esquema se mapea en entidades JPA con relaciones explícitas. La configuración actual utiliza `spring.jpa.hibernate.ddl-auto=update`; no equivale a un sistema versionado de migraciones.

## 4. Roles del Sistema y Matriz de Permisos

Los nombres exactos del enum de roles son `ROLE_CLIENT`, `ROLE_OFFERER` y `ROLE_ADMIN`. La autorización se valida además por propiedad o titularidad cuando corresponde.

| Actor | Permisos principales |
| --- | --- |
| Público / anónimo | Consultar espacios y fichas, buscar por filtros, leer imágenes y reseñas públicas, acceder a OpenAPI/Swagger, registrar una cuenta e iniciar sesión. |
| Cliente (`ROLE_CLIENT`) | Gestionar su perfil y sus reservas, solicitar y cancelar reservas propias, iniciar el pago de una reserva confirmada propia, administrar servicios seleccionados de sus reservas y crear/editar sus reseñas conforme a las validaciones del servicio. |
| Oferente (`ROLE_OFFERER`) | Crear, consultar y modificar sus espacios; administrar los servicios de esos espacios e imágenes; consultar reservas recibidas y confirmar, rechazar o completar las que le pertenecen. La propiedad se comprueba en servidor. |
| Administrador (`ROLE_ADMIN`) | Acceder a operaciones administrativas de usuarios, espacios, reservas y moderación habilitadas por los controladores. Las acciones administrativas específicas se indican en la tabla de endpoints y en Swagger. |

El registro público asigna `ROLE_CLIENT`; no se concede el rol de administrador ni el de oferente a partir de campos enviados por el cliente. La solicitud para pasar a oferente tiene límite de frecuencia de 24 horas y su aprobación es una acción administrativa. Estar autenticado no implica poder operar sobre recursos de otra persona.

## 5. Requisitos Funcionales y No Funcionales Consolidados (Por Módulos)

| Módulo | Funcionalidad y reglas relevantes |
| --- | --- |
| **Autenticación** | Registro, login, JWT Bearer, logout con revocación persistente y BCrypt. La clave JWT se configura fuera del repositorio y debe compartirse entre instancias. |
| **Usuarios** | Perfil, consulta de usuarios, filtros administrativos, cambio de estado/rol, baja lógica y solicitud de rol oferente. El acceso a perfiles y datos sensibles se restringe por identidad o rol. |
| **Espacios y servicios** | Catálogo y filtros públicos, detalle, alta y mantenimiento de espacios propios, moderación administrativa, imágenes y servicios opcionales. Un espacio se puede reservar solo si está activo y el propietario tiene cuenta activa y rol compatible. |
| **Reservas y disponibilidad** | Solicitudes, calendario, consulta de estados, control de autorreserva, solapamientos y buffer, cotización del lado servidor, límite actual de reservas completadas por cliente/espacio, confirmación, rechazo, cancelación y finalización. |
| **Pagos** | Checkout Mercado Pago, webhook y almacenamiento de pagos y reembolsos. Requiere credenciales y URLs de retorno configuradas; los retornos de navegador no son por sí solos confirmación de pago. Este módulo está en desarrollo y no forma parte del MVP. |
| **Reseñas y notificaciones** | Comentarios con puntuación, moderación/activación y consultas de elegibilidad; notificaciones asociadas a usuarios, lectura y contador de no leídas. |

**Requisitos no funcionales:** API HTTP stateless; autenticación y autorización por rol y propiedad; persistencia relacional; DTOs para separar representación y entidades; manejo centralizado de errores; control transaccional y bloqueos para operaciones sensibles a concurrencia; CORS configurable por entorno; y documentación OpenAPI generada desde la aplicación.

## 6. Modelo de Datos Relacional (JPA & MySQL)

Las clases JPA están en `backend/src/main/java/com/utn/space/venueaapi/model/`. Los nombres Java de dominio no siempre coinciden literalmente con los nombres de tabla. Las tablas se generan/actualizan al arrancar mediante Hibernate (`ddl-auto=update`).

| Tabla | Entidad / datos principales | Claves y relaciones |
| --- | --- | --- |
| `consumers` | `Consumer`: `idConsumer`, nombre, apellido, email, teléfono y fecha de solicitud de rol oferente | PK `idConsumer`; relación uno a uno con `credentials`. |
| `credentials` | `Credential`: `username`, contraseña BCrypt, rol, estado y motivo de desactivación | PK `username`; asociada a `consumers`; roles persistidos como enum. |
| `spaces` | `Space`: nombre, descripción, precio base, fecha de publicación, buffer, rating, estado y baja lógica | PK `id_space`; FK al propietario (`consumers`), `locations` y `cancellationpolicies`; colección de `services`. |
| `locations` | `Location`: latitud y longitud | PK `id_location`; referenciada por `spaces`. |
| `cancellationpolicies` | Política, días de anticipación y porcentaje de reintegro | PK `id_cancellation_policies`; referenciada por `spaces`. Las políticas necesarias deben existir antes de publicar; la aplicación no carga automáticamente sus valores de negocio. |
| `spaceimages` | `SpaceImage`: nombre, URL y fecha de envío | PK `id_space_images`; FK al espacio. El almacenamiento de archivos usa S3 cuando está configurado. |
| `services` | `SpaceServiceItem`: descripción, precio y estado del servicio | PK `id_service`; FK al espacio. |
| `reservations` | `Reservation`: título, descripción, fechas, precio final, estado y motivo de cancelación | PK `id_reservation`; FK a cliente y espacio; contiene servicios seleccionados. |
| `servicesselected` | `ServiceSelected`: tarifa y descripción congeladas en la contratación | PK `idServiceSelected`; FK a reserva y servicio del catálogo; incluye `price_at_reservation`. |
| `comments` | `Comment`: descripción, puntuación, estado y fecha | PK `id_comment`; FK a consumidor y espacio. |
| `notifications` | `Notification`: mensaje, fecha y estado de lectura | PK `id_notification`; FK a consumidor destinatario. |
| `revoked_tokens` | Huella del JWT y fecha de vencimiento | PK `token_hash`; índice `idx_revoked_token_expiry`; tabla de revocación para logout. |
| `payments` / `payment_refunds` | Datos de pagos, importes, estado, referencia externa y reintegros | `payments.id_payment` proviene de Mercado Pago; relación con reserva y entidad de reintegro. |

Relaciones centrales: un consumidor puede tener espacios y reservas; cada espacio pertenece a un consumidor, ubicación y política; una reserva vincula un cliente con un espacio; `servicesselected` relaciona una reserva con servicios del catálogo y conserva el precio histórico. Las claves y nombres físicos indicados corresponden a las anotaciones JPA; no hay un DDL versionado en el repositorio.

## 7. Catálogo de Endpoints API REST (OpenAPI / Swagger)

- **Local**: [Swagger UI](http://localhost:8080/swagger-ui/index.html) · [OpenAPI JSON](http://localhost:8080/v3/api-docs).
- **Producción**: no hay una URL pública de API/Swagger declarada en los archivos versionados. El dominio y la distribución de CloudFront dependen de la configuración externa del entorno AWS.
- Todas las rutas de la tabla son relativas a `/api`. Los permisos incluyen los controles de propiedad/titularidad aplicados en métodos cuando corresponda. Swagger es la referencia completa para parámetros y DTOs.

| Módulo | Método | Ruta | Permiso requerido |
| --- | --- | --- | --- |
| Autenticación | `POST` | `/auth/register` | Público |
| Autenticación | `POST` | `/auth/login` | Público |
| Autenticación | `POST` | `/auth/logout` | Autenticado |
| Espacios | `GET` | `/spaces`, `/spaces/{id}` | Público |
| Espacios | `POST` | `/spaces/byfields` | Público |
| Espacios | `GET` | `/spaces/ownedspaces` | Oferente o admin |
| Espacios | `POST` | `/spaces/ownedspace` | Oferente o admin |
| Espacios | `PUT` / `DELETE` | `/spaces/ownedspace/{id}` | Propietario oferente o admin |
| Espacios | `GET` | `/spaces/showinactives` | Admin |
| Espacios | `POST` / `PUT` / `DELETE` | `/spaces` y `/spaces/{id}` | Admin |
| Imágenes | `GET` | `/spaceimages/**` | Público |
| Imágenes | `POST` | `/spaceimages/upload-url`, `/spaceimages` | Oferente propietario o admin |
| Servicios | `GET` | `/services/space/{idSpace}` | Autenticado |
| Servicios | `POST` / `PUT` / `DELETE` | `/services/**` | Oferente o admin; se verifica la propiedad |
| Reservas | `POST` | `/reservations` | Autenticado |
| Reservas | `GET` | `/reservations/me` | Autenticado; propias |
| Reservas | `GET` | `/reservations` | Admin |
| Reservas | `GET` | `/reservations/{id}` | Cliente titular, propietario del espacio o admin |
| Reservas | `GET` | `/reservations/space/{idSpace}/calendar` | Propietario del espacio o admin; otros usuarios ven datos limitados |
| Reservas | `PUT` | `/reservations/confirm/{id}`, `/reject/{id}`, `/complete/{id}` | Propietario del espacio o admin |
| Reservas | `PUT` | `/reservations/cancel/{id}` | Cliente titular, propietario o admin |
| Pagos | `POST` | `/reservations/{id}/checkout` | Cliente titular; reserva confirmada |
| Selecciones | `GET` / `POST` / `DELETE` | `/servicesselected/reservation/{idReservation}` y rutas `/servicesselected/**` | Cliente titular o admin; lectura también para propietario |
| Reseñas | `GET` | `/comments/byspaceid/{id}` | Público |
| Reseñas | `POST` / `PUT` | `/comments`, `/comments/{id}` | Autenticado; se comprueba la elegibilidad/autoría |
| Usuarios | `GET` / `DELETE` | `/usuarios`, `/usuarios/{id}` | Admin; la lectura individual permite al titular |
| Usuarios | `PUT` / `DELETE` | `/usuario` | Autenticado; perfil propio |
| Notificaciones | `GET` | `/notifications/me`, `/notifications/unread-count` | Autenticado |
| Webhook de pagos | `POST` | `/v1/payments/webhook` | Endpoint de integración; validar configuración del proveedor |

El prefijo del webhook está versionado en `/api/v1/payments/webhook`, a diferencia de las rutas anteriores, cuyo prefijo común `/api` se omite en la tabla. Para iniciar sesión, enviar el token resultante como `Authorization: Bearer <JWT>`. El login devuelve el JWT como texto plano, según el contrato vigente.

## 8. Tecnologías Utilizadas y Requisitos de Versionado

| Tecnología | Versión / detalle del repositorio |
| --- | --- |
| Java | 17 (LTS), configurado en `backend/pom.xml`. |
| Spring Boot | 4.0.6, versión declarada en el parent Maven. **No es Spring Boot 3.x.** |
| Spring Web, Security, Validation, Data JPA | Starters de Spring Boot. |
| Persistencia | Hibernate/JPA; `ddl-auto=update`. El dialecto configurado es `org.hibernate.dialect.MariaDBDialect`, aunque se usa el driver Connector/J de MySQL. |
| Base de datos | MySQL compatible, URL y credenciales configurables; el proyecto no fija una versión de servidor en Maven. |
| Driver MySQL | `com.mysql:mysql-connector-j`, scope runtime. |
| Build | Maven Wrapper incluido en `backend/` (Maven 3.9.16). |
| OpenAPI / Swagger | `springdoc-openapi-starter-webmvc-ui` 3.0.2. **No es Springdoc v2.** |
| JWT | JJWT 0.12.5; firma HS256. |
| Integraciones | SDK Java de Mercado Pago 3.2.0 y AWS SDK S3 2.56.0. |
| Pruebas | H2 y starters de test de Spring Boot; las dependencias de test están declaradas en `pom.xml`. |

El README original y los documentos locales describen Spring Boot 4 en algunos lugares; para versiones prevalece el `pom.xml`. Se recomienda una instancia MySQL 8.x para desarrollo, pero la versión mínima/máxima no está fijada por el proyecto.

## 9. Variables de Entorno

La aplicación lee variables de entorno del proceso. **No carga automáticamente archivos `.env`**; `backend/.env.example` es una plantilla que debe copiarse/adaptarse y sus valores deben exportarse desde la terminal, IDE o gestor de secretos. No incluir secretos reales en el repositorio.

| Variable | Obligatoria | Uso / valor por defecto |
| --- | --- | --- |
| `PORT` | No | Puerto HTTP; `8080` por defecto. |
| `SPRING_DATASOURCE_URL` | Sí | JDBC URL, por ejemplo `jdbc:mysql://localhost:3306/space_venue_db?serverTimezone=UTC`. |
| `SPRING_DATASOURCE_USERNAME` | Sí | Usuario de la base de datos. |
| `SPRING_DATASOURCE_PASSWORD` | Sí | Contraseña de la base; valor secreto, no versionar. |
| `JWT_SECRET_BASE64` | Sí | Clave aleatoria Base64 de al menos 32 bytes, estable entre reinicios e instancias. No hay clave insegura por defecto. |
| `CORS_ALLOWED_ORIGINS` | No | Lista separada por comas, sin barra final. Por defecto: `http://localhost`, `http://127.0.0.1`, puertos locales `3000`, `5173` y `4200`. |
| `MP_ACCESS_TOKEN` | No para iniciar | Token privado de Mercado Pago. Se requiere para habilitar pagos. |
| `PAYMENT_SUCCESS_URL` | No para iniciar | URL de retorno de pago aprobado; configurar junto con las otras dos para habilitar el flujo. |
| `PAYMENT_FAILURE_URL` | No para iniciar | URL de retorno de pago rechazado. |
| `PAYMENT_PENDING_URL` | No para iniciar | URL de retorno de pago pendiente. |
| `AWS_REGION` | No | Región AWS; valor predeterminado `us-east-1`. |
| `AWS_S3_BUCKET` | No | Bucket para imágenes; vacío por defecto. |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | No en EC2 | El SDK puede usar credenciales locales en desarrollo; en EC2 se recomienda el rol IAM asociado a la instancia. |

`application.properties` está en `backend/src/main/resources/`. La configuración del servicio EC2 referencia `/etc/space-venue-api/app.env`; ese archivo se administra en el servidor y no debe publicarse.

## 10. Pasos de Instalación y Requisitos de Runtime Local

**Requisitos:** Java 17 o superior, MySQL Server disponible en `localhost:3306`, Git y acceso a Internet para descargar dependencias Maven. Puede usarse MySQL instalado localmente, Docker u otra distribución compatible.

1. Clonar el repositorio:

   ```bash
   git clone https://github.com/FeijooCiro4/space-venue-api.git
   cd space-venue-api
   ```

2. Crear la base de datos desde un cliente MySQL:

   ```sql
   CREATE DATABASE space_venue_db;
   ```

3. Configurar el proceso con las variables de la sección anterior. Generar una clave JWT nueva para desarrollo:

   ```bash
   openssl rand -base64 32
   ```

   Con Bash, exportar variables de conexión y JWT antes de iniciar (reemplazar las credenciales de ejemplo):

   ```bash
   export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/space_venue_db?serverTimezone=UTC'
   export SPRING_DATASOURCE_USERNAME='spacevenue'
   export SPRING_DATASOURCE_PASSWORD='<CONTRASENA_LOCAL>'
   export JWT_SECRET_BASE64='<CLAVE_BASE64_GENERADA>'
   ```

   En PowerShell, usar `$env:SPRING_DATASOURCE_URL="..."`, `$env:SPRING_DATASOURCE_USERNAME="..."`, `$env:SPRING_DATASOURCE_PASSWORD="..."` y `$env:JWT_SECRET_BASE64="..."`. No guardar secretos reales en el `.env.example`.

4. Iniciar desde la carpeta del backend:

   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```

   En Windows: `.\mvnw.cmd spring-boot:run`.

5. Verificar `http://localhost:8080/swagger-ui/index.html`. El contrato OpenAPI queda en `http://localhost:8080/v3/api-docs`.

El puerto predeterminado es `8080` (`PORT` permite cambiarlo). Hibernate crea/actualiza las tablas al iniciar. Antes de probar altas de espacios, provisionar las políticas de cancelación requeridas: no hay carga automática de datos semilla y el repositorio no incluye un script SQL de inicialización. Un ejemplo de inicialización de políticas:

```sql
INSERT INTO cancellationpolicies (type, days_anticipation, refund_percentage) 
VALUES ('FLEXIBLE', 3, 100.00);

INSERT INTO cancellationpolicies (type, days_anticipation, refund_percentage) 
VALUES ('MODERATED', 7, 50.00);

INSERT INTO cancellationpolicies (type, days_anticipation, refund_percentage) 
VALUES ('STRICT', 14, 0.00);
```

## 11. Despliegue en la Nube (AWS Architecture)

La configuración versionada muestra un flujo de despliegue a EC2 con un JAR ejecutado como servicio systemd. Los recursos de AWS (IP, instancia, dominios, CloudFront, bucket, secretos, rol IAM y parámetros de GitHub) se configuran fuera del repositorio.

- **Infraestructura de aplicación**: `.github/workflows/deploy-backend.yml` compila el proyecto para Java 17 en pushes a `master` que modifican `backend/**`, `deploy/ec2/**` o el propio workflow, y también permite ejecución manual. El artefacto se ejecuta en EC2 mediante el servicio definido en `deploy/ec2/space-venue-api.service` (nombre habitual `space-venue-api`, no `space-venue.service`). No se encontró una configuración Docker del backend en este repositorio.
- **CI/CD**: GitHub Actions obtiene credenciales AWS mediante OIDC, sube el JAR a S3 bajo una ruta con el SHA del commit y solicita a Systems Manager (SSM) que ejecute `deploy/ec2/deploy.sh` en la instancia. El workflow requiere variables de repositorio/entorno, entre ellas `AWS_REGION`, `DEPLOY_BUCKET`, `EC2_INSTANCE_ID` y `AWS_DEPLOY_ROLE_ARN`.
- **Reinicio y rollback**: El script descarga `app-new.jar`, guarda el JAR actual como `app-prev.jar`, lo instala como `app.jar`, reinicia systemd y comprueba `/v3/api-docs` en el puerto configurado. Si la nueva versión no responde, intenta restaurar `app-prev.jar`.
- **Imágenes y borde HTTPS**: `application.properties` contempla S3 para imágenes, y la configuración de almacenamiento indica que pueden servirse desde CloudFront bajo `/media/*`. Sin embargo, la distribución, certificado SSL, Elastic IP, reglas de red y dominio no están declarados en los archivos disponibles; el detalle “HTTPS perimetral en puerto 8080” no puede confirmarse a partir del código versionado.
- **URLs públicas**: [API](https://d3l047wlzqnbc6.cloudfront.net/api), [Swagger](https://d3l047wlzqnbc6.cloudfront.net/swagger-ui.html) y [Almacenamientio de imágenes](https://d3l047wlzqnbc6.cloudfront.net).

El servicio systemd espera un entorno privado en `/etc/space-venue-api/app.env` y ejecuta `/opt/space-venue-api/app.jar` como usuario `spacevenue`. La instancia necesita SSM Agent, permisos IAM apropiados y acceso al bucket de artefactos; no guardar credenciales AWS ni variables secretas en el repositorio.

## 12. Equipo de Desarrollo

- Ciro Luciano Feijoo
- Marcos Hoyuelos
- Federico Facundo Huergo
- Lautaro Daniel Perez
