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

## Sesiones y creación concurrente de reservas

`JwtUtil` usa `JWT_SECRET_BASE64`, una clave privada estable de al menos 32 bytes aleatorios en Base64, sin valor predeterminado de producción. Cada JWT conserva la duración de diez horas y recibe un `jti` único para distinguir sesiones del mismo usuario creadas en el mismo segundo.

`TokenBlacklistService` persiste en `revoked_tokens` la huella SHA-256 y la fecha `exp` exacta del token mediante `RevokedTokenRepository`. Las consultas de revocación usan esa base compartida, por lo que funcionan entre instancias y tras reinicios. `@EnableScheduling` habilita la limpieza horaria de filas ya vencidas. La configuración y la nueva tabla se describen en [backend/README.md](../backend/README.md#clave-jwt-y-logout).

`ReservationService.create` se ejecuta en una transacción `READ_COMMITTED`. Antes de comprobar disponibilidad, `SpaceService.findByIdForUpdate` → `SpaceRepository.findByIdForUpdate` adquiere un bloqueo pesimista de escritura sobre la fila del espacio. Se bloquea el espacio y no solo sus reservas, para cubrir también la primera reserva. El bloqueo se mantiene hasta confirmar o revertir reserva, servicios seleccionados y notificación. Una solicitud que esperaba vuelve a consultar las reservas ya confirmadas en la base; los demás espacios pueden reservarse en paralelo. En MySQL se requiere un motor transaccional con bloqueos de fila, como InnoDB.

Esta protección cubre altas concurrentes mediante `create`; la edición y las transiciones de estado conservan los pendientes de negocio indicados debajo.

## Pendientes identificados en el código

Los siguientes problemas existían antes de reorganizar el proyecto y requieren una etapa específica de corrección.

| Hallazgo | Evidencia | Trabajo pendiente |
| --- | --- | --- |
| Webhook de pagos incompleto | `PaymentWebhookController`, `SecurityConfig`, `PaymentServiceImpl` | La ruta exige autenticación de la aplicación y contiene una simulación que puede confirmar reservas. Separar simulación de producción, verificar autenticidad del proveedor y después configurar acceso al webhook. No se abrió públicamente esa ruta. |
| Estados de pago y reserva mezclados | `PaymentServiceImpl.processNotification`, `ReservationService` | Confirmación del anfitrión y pago aprobado usan `CONFIRMED`; revisar transiciones, validación de importe/moneda e idempotencia. |
| Cambio de estado de usuario sin implementación | `ConsumerController.toggleUserStatus` | Responde éxito sin modificar datos y requiere `active`, que es obligatorio en la solicitud. |
| Políticas sin datos iniciales | `CancellationPoliciesService`, `EPolicyType` | Crear un espacio exige que la política exista en MySQL. No hay seed/migración de políticas; definir valores reales antes de cargar catálogo. |
| Reglas de disponibilidad pendientes fuera de la creación concurrente | `ReservationService.modify` y cambios de estado | Extender la comprobación de disponibilidad y el bloqueo por espacio a la edición y a transiciones que vuelvan a ocupar un horario. Revisar también duración cero e inactividad del espacio/servicios. |
| Lecturas de notificaciones con efectos | `NotificationService.listAllByIdConsumerForConsumer` | Listar marca como vistas todas las notificaciones devueltas. Definir una operación de lectura sin mutación. |
| Contratos pendientes fuera del catálogo | Controladores y `GlobalExceptionHandler` | Espacios e imágenes ya usan DTO de salida sin datos de contacto ni credenciales del propietario. Otras respuestas autorizadas aún mezclan entidades, texto y mapas; queda pendiente uniformar esos contratos y errores. |
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

Las pruebas Java cubren arranque, CORS, catálogo/OpenAPI, filtros, protección de contraseñas, ausencia del contrato retirado y regresión de reservas. `ResourceOwnershipTests` agrega 33 casos con JWT reales para accesos ajenos, titulares, dueños y administradores, incluyendo falsificación de IDs y cambios de estado por el PUT general. `AccountDeactivationTests` verifica login de cuentas activas e inactivas, baja propia de CLIENT/ADMIN, baja administrativa, rechazo de JWT anteriores y tokens de cuentas inexistentes. `JwtLifecycleTests` verifica clave compartida, sesiones distintas, revocación tras recrear el servicio, limpieza por vencimiento y rechazo de tokens inválidos. `ReservationConcurrencyTests` usa hilos y transacciones independientes para comprobar altas simultáneas, independencia entre espacios y rollback ante fallos de notificación. H2 no sustituye la validación con MySQL ni una compra real con Mercado Pago.

## Correcciones de servicios y espacios

`ServiceSelectedService` recibe `SelectServiceDTO` (ID del catálogo), congela precio y descripción
desde `SpaceServiceItem` y actualiza selección y total en una transacción con bloqueo de la
reserva. Alta, baja individual y baja completa mantienen la colección administrada; los
borrados usan `orphanRemoval`. El DTO de lectura no cambia. No se modificó el esquema de pagos.

`SpaceService.modifySpace` y `modifyOwnedSpace` cargan y refrescan la entidad bajo bloqueo,
validan los datos y actualizan solo los campos editables. Se conservan propietario, actividad,
publicación y servicios. En edición el propietario puede omitirse; un propietario distinto
se rechaza incluso en la ruta administrativa. La ruta propia verifica además al usuario actual.

## Privacidad pública y registro unificado

`PublicSpaceMapper` define una lista explícita de campos de lectura para espacios e imágenes.
`SpaceResponseDTO.PublicOwner` solo contiene ID, nombre y apellido. Los DTO anidados no contienen
entidades, por lo que agregar propiedades al modelo persistente no amplía el catálogo público.
La relación `Space.consumerOwner` también excluye contacto y credenciales cuando se serializa
dentro de otras respuestas, como reservas. El perfil privado conserva sus datos de contacto.

`AuthController.register` y `ConsumerController.createUser` delegan en `RegistrationService`.
La única llamada a BCrypt para altas está en `CredentialService.createCredential`. Se persiste
un Consumer nuevo con su credencial nueva en cascada dentro de una transacción; no se hace merge
de una credencial recibida del cliente. La clave única de username impide sobrescribir cuentas
en carreras de registro. Las pruebas verifican login por ambas rutas, reversión completa y carreras.
No hay migración de esquema ni reparación automática de contraseñas previamente codificadas dos veces.
