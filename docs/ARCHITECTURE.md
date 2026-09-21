# Arquitectura del backend

La aplicación es una API REST de reservas. El proyecto Java está en `backend/`, con construcción Maven y Docker independiente. El entorno local contiene únicamente la API y MySQL.

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

`docker-compose.yml` inicia `db` y `backend`. `render.yaml` define únicamente el servicio de la API, con contexto `backend/`. Las variables privadas pertenecen al proceso del servidor. Ver [README](../README.md) y [contrato HTTP](API.md).

## Pendientes identificados en el código

Los siguientes problemas existían antes de reorganizar el proyecto y requieren una etapa específica de corrección.

| Hallazgo | Evidencia | Trabajo pendiente |
| --- | --- | --- |
| Permisos de reservas incompletos | `ReservationController`, `ReservationService` | Limitar consultas y cambios de estado al cliente/propietario correspondiente; la consulta global devuelve todas las reservas. |
| Autorización incompleta en otros recursos | `SpaceImageService`, `ServiceSelectedService`, `NotificationService`, `CommentService.consumerModifyCommentOnSpace` | Mutaciones por ID y comparación con `idConsumer` del DTO requieren validación sobre el registro almacenado. |
| Webhook de pagos incompleto | `PaymentWebhookController`, `SecurityConfig`, `PaymentServiceImpl` | La ruta exige autenticación de la aplicación y contiene una simulación que puede confirmar reservas. Separar simulación de producción, verificar autenticidad del proveedor y después configurar acceso al webhook. No se abrió públicamente esa ruta. |
| Estados de pago y reserva mezclados | `PaymentServiceImpl.processNotification`, `ReservationService` | Confirmación del anfitrión y pago aprobado usan `CONFIRMED`; revisar transiciones, validación de importe/moneda e idempotencia. |
| Sesiones y bajas de usuario | `JwtUtil`, `TokenBlacklistService`, `CustomUserDetailsService` | Clave JWT nueva en cada arranque; blacklist de una hora frente a JWT de diez horas; el adaptador no traslada el estado deshabilitado al UserDetails final. |
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

Desde la raíz se puede validar la orquestación con `docker compose --env-file .env.example config --quiet`.

Las siete pruebas Java cubren arranque, CORS, catálogo/OpenAPI, filtros, protección de contraseñas, ausencia del contrato retirado y regresión de reservas. H2 no sustituye la validación con MySQL ni una compra real con Mercado Pago.
