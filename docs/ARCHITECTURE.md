# Arquitectura del backend

La aplicación es una API REST de reservas. El proyecto Java está en `backend/`, con construcción Maven independiente. El servidor se ejecuta como proceso Java y se conecta a MySQL.

## Responsabilidades

| Capa | Ubicación en `backend/src/main/java/com/utn/space/venueaapi/` | Función |
| --- | --- | --- |
| HTTP | `controllers/` | Rutas, solicitudes y respuestas |
| Negocio | `service/` | Disponibilidad, precios, reservas, usuarios, pagos y notificaciones |
| Persistencia | `model/`, `repository/` | Entidades y acceso a MySQL |
| Contrato | `model/records/`, `service/mappers/` | DTO y transformaciones |
| Seguridad | `security/`, `config/SecurityConfig.java` | JWT y autorización |
| Configuración | `config/`, `src/main/resources/application.properties` | CORS, serialización y Mercado Pago |
| Errores | `exceptions/` | Excepciones del dominio y respuestas HTTP |

```text
Consumidor HTTP ── /api + Bearer ──> Spring Boot ──> MySQL
                                        │
                                        └──> Mercado Pago
```

Swagger UI documenta el contrato del servidor. CORS define los orígenes autorizados a consumirlo. Los destinos de retorno de Mercado Pago se configuran por despliegue y no tienen valores predeterminados.

## Ejecución

El punto de entrada es `com.utn.space.venueaapi.Application`. Se ejecuta desde el IDE, con `./mvnw spring-boot:run` o mediante el JAR compilado. Las variables privadas pertenecen al proceso del servidor. Ver [README](../README.md) y [contrato HTTP](API.md).

## Permisos sobre recursos

Los controladores reutilizan `@PreAuthorize` y `SecurityUtils` para comprobar la propiedad en la base de datos. La edición y eliminación de comentarios verifican al autor almacenado en `CommentService`. `GlobalExceptionHandler` responde 403 ante `AccessDeniedException`.

| Recurso / operación | Usuarios autorizados |
| --- | --- |
| Lista global de reservas | ADMIN |
| Consulta de reserva y de sus servicios seleccionados | Cliente titular, dueño del espacio o ADMIN |
| Edición de reserva y cambios de servicios seleccionados | Cliente titular o ADMIN; el cliente no puede transferir la reserva |
| Confirmar, rechazar o completar reserva | Dueño del espacio o ADMIN |
| Cancelar reserva | Cliente titular, dueño del espacio o ADMIN |
| Baja lógica de reserva | ADMIN |
| Crear, editar o borrar imágenes | Dueño del espacio o ADMIN; al editar se comprueban tanto la imagen guardada como el espacio de destino |
| Consultar o marcar notificación por ID | Destinatario o ADMIN |
| Editar o borrar comentario | Autor guardado o ADMIN |
| Consultar perfil por ID | El propio usuario o ADMIN |

La edición general de reservas conserva estado, actividad y fecha de creación. Los permisos del checkout se mantienen: CLIENT titular de la reserva. Las lecturas públicas del catálogo, imágenes y comentarios por espacio conservan su acceso público.

## Cuentas desactivadas

`CustomUserDetailsService` traslada `Credential.isActive` al estado habilitado de Spring Security. El login rechaza cuentas desactivadas con 401 y el mismo mensaje usado para credenciales inválidas.

En cada solicitud con un JWT válido y fuera de la blacklist, `JwtFilter` vuelve a consultar la credencial mediante ese servicio. Si la cuenta está desactivada o ya no existe, responde 401 antes de ejecutar el controlador. La baja propia y administrativa bloquea así las siguientes solicitudes de tokens ya emitidos. Esta comprobación requiere una consulta de credenciales por solicitud; no cancela operaciones que ya estaban en ejecución al realizarse la baja.

## Pendientes identificados en el código

Los siguientes problemas existían antes de reorganizar el proyecto y requieren una etapa específica de corrección.

| Hallazgo | Evidencia | Trabajo pendiente |
| --- | --- | --- |
| Webhook de pagos incompleto | `PaymentWebhookController`, `SecurityConfig`, `PaymentServiceImpl` | La ruta exige autenticación de la aplicación y contiene una simulación que puede confirmar reservas. Separar simulación de producción, verificar autenticidad del proveedor y después configurar acceso al webhook. No se abrió públicamente esa ruta. |
| Estados de pago y reserva mezclados | `PaymentServiceImpl.processNotification`, `ReservationService` | Confirmación del anfitrión y pago aprobado usan `CONFIRMED`; revisar transiciones, validación de importe/moneda e idempotencia. |
| Persistencia y revocación de sesiones | `JwtUtil`, `TokenBlacklistService` | Clave JWT nueva en cada arranque; blacklist de una hora frente a JWT de diez horas. |
| Registro alternativo duplica hashing | `ConsumerController.createUser`, `CredentialService.saveCredential` | `/api/usuarios` codifica la contraseña dos veces. Usar `/api/auth/register` y unificar registro en una etapa posterior. |
| Cambio de estado de usuario sin implementación | `ConsumerController.toggleUserStatus` | Responde éxito sin modificar datos y requiere `active`, que es obligatorio en la solicitud. |
| Edición de espacios propios inconsistente | `SpaceDTO`, `SpaceService.modifyOwnedSpace` y `modifySpace` | El grupo Update exige owner, aunque el propietario se obtiene de la sesión; luego se recrea la entidad sin conservar claramente estado y relaciones. Diseñar DTO de edición específico y actualizar la entidad existente. |
| Políticas sin datos iniciales | `CancellationPoliciesService`, `EPolicyType` | Crear un espacio exige que la política exista en MySQL. No hay seed/migración de políticas; definir valores reales antes de cargar catálogo. |
| Disponibilidad sin protección concurrente | `ReservationService.create` | Comprobación y escritura separadas; falta resolver carreras entre solicitudes simultáneas. Revisar también duración cero e inactividad del espacio/servicios. |
| Lecturas de notificaciones con efectos | `NotificationService.listAllByIdConsumerForConsumer` | Listar marca como vistas todas las notificaciones devueltas. Definir una operación de lectura sin mutación. |
| Contrato expone entidades y errores heterogéneos | Controladores y `GlobalExceptionHandler` | Aún hay datos personales anidados, texto plano, mapas y entidades completas. Incorporar DTO de salida y un formato de errores estable con una transición compatible. |
| Validaciones incompletas | `AuthController.register`, DTO y grupos Create/Update | Algunas restricciones no se invocan o usan grupos inadecuados. Validar todos los payloads en el servidor. |
| Unidad de separación entre reservas | `ReservationService.isSpaceAvailableBetweenDates` | Documentar y validar `bufferTime` en minutos enteros, conforme a `plusMinutes`. |
| Almacenamiento de imágenes | `SpaceImage` | La columna de URL no declara almacenamiento largo y no existe un servicio de archivos. Definir alojamiento y límites de tamaño. |

La limpieza del dominio pendiente incluye trasladar registro/perfil de controladores a servicios, utilizar DTO de salida y completar las verificaciones de permisos.

## Integración retirada

Google Calendar fue eliminado del código, las dependencias y la configuración. Crear una reserva termina al guardarla y notificar al dueño. En una base nueva no se crea su tabla de tokens ni sus campos de sincronización.

Hibernate con `ddl-auto=update` no borra tablas o columnas históricas de una base existente. Esos datos pueden quedar sin uso hasta una migración explícita. No se modificó la base existente durante la reorganización.

## Verificación

```bash
cd backend
./mvnw test
./mvnw package
```

Las pruebas Java cubren arranque, CORS, catálogo/OpenAPI, filtros, protección de contraseñas, ausencia del contrato retirado y regresión de reservas. `ResourceOwnershipTests` agrega 33 casos con JWT reales para accesos ajenos, titulares, dueños y administradores, incluyendo falsificación de IDs y cambios de estado por el PUT general. `AccountDeactivationTests` verifica login de cuentas activas e inactivas, baja propia de CLIENT/ADMIN, baja administrativa, rechazo de JWT anteriores y tokens de cuentas inexistentes. H2 no sustituye la validación con MySQL ni una compra real con Mercado Pago.
