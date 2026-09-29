# Panorama y recorrido de la API

Esta guía resume el recorrido implementado de cada operación: **controlador → servicio → repositorio**, los objetos de `model` que intervienen y las excepciones principales. Todas las rutas son relativas a **`/api`**. Para ejemplos de solicitudes, ver [API.md](API.md); para pendientes de arquitectura, ver [ARCHITECTURE.md](ARCHITECTURE.md).

## Cómo leer la guía

- El controlador indicado en cada sección es el punto de entrada de todas sus filas; el recorrido comienza con el nombre de su método.
- **Público**: no requiere JWT. **JWT**: requiere autenticación. **CLIENT/ADMIN**: además exige alguno de esos roles. **ADMIN** o **CLIENT**: exige ese rol específico.
- Los modelos enumerados son los que se reciben, consultan, crean o modifican en el flujo. Los DTO están en `model/records`; las entidades pueden incluir otras relaciones anidadas en la respuesta.
- **—** en excepciones significa que el flujo no lanza explícitamente una excepción de negocio; no descarta errores de formato, persistencia o infraestructura.
- **Validación DTO** significa `MethodArgumentNotValidException` al incumplir las restricciones activas del DTO. Los grupos `Create`, `Update`, `CreateAdmin` y `UpdateAdmin`, en `model/flags`, seleccionan esas restricciones; no son entidades.

## Recorrido común y JWT

```text
Solicitud HTTP
  → JwtFilter + SecurityConfig (y permisos del método)
  → Controller
  → Service → otros servicios, cuando corresponde
  → Repository → base de datos
  → respuesta del controlador

Si se propaga una excepción desde el controlador/servicio:
  → GlobalExceptionHandler → respuesta de error
```

El cliente envía `Authorization: Bearer <token>`. `JwtFilter` usa `JwtUtil` para leer y validar el token, consulta `TokenBlacklistService` → `RevokedTokenRepository.existsByTokenHashAndExpiresAtAfter` y verifica la cuenta mediante `CustomUserDetailsService.loadUserByUsername` → `CredentialRepository.findByUsername`. Solo si la credencial existe y está activa carga el usuario y su rol en el contexto de seguridad. Una cuenta desactivada o inexistente recibe 401 antes del controlador, incluso si el JWT todavía no venció. Esta verificación agrega una consulta de credenciales por solicitud autenticada con JWT.

Cuando un servicio necesita el ID del usuario actual, usa **`ConsumerService.getLoggedConsumerId()` → `findByCredentialsUsername()` → `ConsumerRepository.findByUsername()`**. En las tablas, **usuario actual** representa este recorrido compartido: intervienen `Consumer` y `Credential`, y pueden aparecer `NameNotFoundException` o `IllegalStateException` si no se puede resolver la identidad. Estas excepciones se agregan a las específicas de cada fila que usa ese recorrido.

El login genera un JWT con `username` como subject, `rol`, `consumerId` y un `jti` único por sesión. Dura diez horas, se firma con la clave estable `JWT_SECRET_BASE64` y el logout registra su huella en la base hasta el vencimiento real. Los permisos de `@PreAuthorize` también se aplican aunque una regla general de `SecurityConfig` permita la ruta, como ocurre con algunos GET de espacios.

### Controles de propiedad

Antes de ejecutar las operaciones protegidas, `@PreAuthorize` consulta `SecurityUtils`: reservas → `ReservationRepository.findById`; perfiles → `ConsumerRepository.findById`; espacios e imágenes → `SpaceRepository.findById` y `SpaceImageRepository.findById`; servicios seleccionados → `ServiceSelectedRepository.findById`; notificaciones → `NotificationRepository.findById`. Se compara el username del JWT con el usuario relacionado en la base. ADMIN tiene acceso general, salvo los permisos específicos ya existentes del checkout.

Una denegación produce `AccessDeniedException` y HTTP 403. Para un usuario no administrador, un ID inexistente también puede rechazarse con 403 antes de llegar al servicio. Las excepciones de las tablas describen el flujo posterior a superar estos controles. Ver la [matriz de permisos](ARCHITECTURE.md#permisos-sobre-recursos).

## Autenticación — `AuthController`

| Llamado y acceso | Recorrido y resultado | Objetos de model | Excepciones / errores |
| --- | --- | --- | --- |
| `POST /auth/login` · Público | `login` → `AuthenticationManager` → `CustomUserDetailsService.loadUserByUsername` → `CredentialRepository.findByUsername`; luego `ConsumerService.findByCredentialsUsername` → `ConsumerRepository.findByUsername` → `JwtUtil.generarToken`. Devuelve el texto `Bearer <token>`. | `Credential`, `Consumer`, `ERoles`; entrada: mapa de username/password. | `UsernameNotFoundException` en la carga del usuario; `BadCredentialsException` o `DisabledException` de autenticación, capturadas como 401; `NameNotFoundException` si falta el perfil. |
| `POST /auth/register` · Público | `RegistrationService.register` → comprueba username → `CredentialService.createCredential` aplica BCrypt una vez → `ConsumerRepository.saveAndFlush` persiste credencial y perfil en una transacción. No emite JWT. | `RegistroDTO`, `Consumer`, `Credential` con rol CLIENT. | `InvalidDataException` (400) por credenciales vacías, username repetido o conflicto de persistencia. |
| `POST /auth/logout` · Ruta pública; necesita Bearer para cerrar sesión | `logout` → `TokenBlacklistService.blacklistToken` → `JwtUtil.extraerClaims` → `RevokedTokenRepository.save`. Guarda la huella SHA-256 y la fecha de vencimiento exacta. | `RevokedToken`; trabaja con el JWT. | Devuelve 400 si no recibe cabecera Bearer. Un token revocado, inválido o expirado es rechazado antes por el filtro con 401. |

## Usuarios y perfil — `ConsumerController`

| Llamado y acceso | Recorrido y resultado | Objetos de model | Excepciones / errores |
| --- | --- | --- | --- |
| `POST /usuarios` · Público | `createUser` adapta username/password a un perfil vacío → `RegistrationService.register`, igual que `/auth/register`. | `CredentialRegistrationDTO`, `RegistroDTO`, `Consumer`, `Credential`. | Validación DTO; `InvalidDataException` (400). |
| `GET /usuarios` · ADMIN | `listAllUsers` → `ConsumerService.findAll` → `ConsumerRepository.findAll`. | `Consumer`, `Credential`. | — |
| `GET /usuarios/{id}` · Perfil propio o ADMIN | `listById` → `ConsumerService.findById` → `ConsumerRepository.findById`. | `Consumer`, `Credential`. | `IdNotFoundException`. |
| `GET /usuarios/byfields` y `POST /usuarios/byfields` · ADMIN | `findAllByFields` → `ConsumerService.findAllByfields` → `ConsumerRepository.findAllByFilters`. Ambos reciben filtros en el cuerpo. | `ConsumerFilterDTO`, `Consumer`, `Credential`. | — |
| `PUT /usuarios/{id}/status?active=...` · ADMIN | `toggleUserStatus` devuelve éxito directamente. **No llama a servicio ni repositorio y no cambia el estado.** | Ninguno. | Sin excepción de negocio; `active` es un parámetro obligatorio. |
| `DELETE /usuarios/{id}` · ADMIN | `deleteById` → `ConsumerService.deleteUserLogicallyById` → `findById` → `ConsumerRepository.findById/save`. Desactiva la credencial: bloquea nuevos logins y el uso de JWT anteriores en las siguientes solicitudes. | `Consumer`, `Credential`. | `IdNotFoundException`; `RuntimeException` si no hay credenciales. |
| `PUT /usuario` · JWT | `updateUser` toma el username del principal JWT → `ConsumerService.findByUsername`, `existByEmail`, `existsByPhone`, `updateUser` → `ConsumerRepository.findByUsername/existsByEmail/existsByPhone/existsById/save`. | `ConsumerFilterDTO`, `Consumer`, `Credential`. | `NameNotFoundException`, `IdNotFoundException`; `RuntimeException` si email o teléfono ya están usados. |
| `DELETE /usuario` · JWT | `deleteUser` toma el username del principal JWT → `ConsumerService.deleteUserLogically` → `ConsumerRepository.findByUsername/save`. Desactiva la credencial del usuario: bloquea nuevos logins y el uso de JWT anteriores en las siguientes solicitudes. | `Consumer`, `Credential`. | `RuntimeException` si no encuentra usuario o credenciales. |

Ambas rutas de registro comparten una transacción y un solo hashing. Se mantienen sus respuestas 201; los datos de rol y actividad se fijan en el servidor.

## Espacios — `SpaceController`

En esta sección, **resolver relaciones** significa: `ConsumerService.findById` → `ConsumerRepository.findById`; `LocationService.findByLongitudeAndLatitude` → `LocationRepository.findLocationByLongitudeAndLatitude`; `CancellationPoliciesService.findByType` → `CancellationPoliciesRepository.findByType`.

Los filtros verifican los IDs opcionales mediante `ConsumerService.existsById` → `ConsumerRepository.existsById` y `LocationService.existsById` → `LocationRepository.existsById`. Si reciben coordenadas, `LocationService.isSpaceNearby` filtra los resultados por distancia sin otra consulta de base de datos.

| Llamado y acceso | Recorrido y resultado | Objetos de model | Excepciones / errores |
| --- | --- | --- | --- |
| `GET /spaces` · Público | `listActiveSpaces` → `SpaceService.findAllActives` → `SpaceRepository.findAllWithOutInactives`. Catálogo activo. | `Space`. | — |
| `GET /spaces/{id}` · Público | `findSpaceById` → `SpaceService.findById` → `SpaceRepository.findById`. No filtra por estado activo. | `Space`. | `IdNotFoundException`. |
| `GET /spaces/showinactives` · ADMIN | `listSpaces` → `SpaceService.findAll` → `SpaceRepository.findAll`. Incluye inactivos. | `Space`. | — |
| `POST /spaces/byfields` · Público | `findAllByFields` → `SpaceService.findAllByFields` → verificaciones de filtros → `SpaceRepository.findAllByFields` → filtro de proximidad opcional. | `SpaceFilterDTO`, `Space`, `Location`; referencia a `Consumer`. | `IdNotFoundException` por propietario o ubicación inexistentes. |
| `GET /spaces/byfields/showinactives` y `POST /spaces/byfields/showinactives` · ADMIN | `findAllByFieldsWithInactives` → método homónimo de `SpaceService` → verificaciones de filtros → `SpaceRepository.findAllByFieldsWithInactives` → proximidad opcional. | `SpaceFilterDTO`, `Space`, `Location`; referencia a `Consumer`. | `IdNotFoundException`. |
| `GET /spaces/ownedspaces` · CLIENT/ADMIN | `findAllForOwner` → `SpaceService.findAllForOwner` → **usuario actual** → `findAllByFieldsWithInactives` → verificaciones de filtros → `SpaceRepository.findAllByFieldsWithInactives`. | `SpaceFilterDTO` interno, `Space`, `Consumer`. | `IdNotFoundException`; errores de usuario actual. |
| `GET /spaces/ownedspaces/byfields` y `POST /spaces/ownedspaces/byfields` · CLIENT/ADMIN | `findAllOwnedSpacesbyFields` → `SpaceService.findAllByFieldsForOwner` → **usuario actual** → `findAllByFieldsWithInactives` → verificaciones de filtros → `SpaceRepository.findAllByFieldsWithInactives`. Fuerza el propietario de la sesión. | `SpaceFilterDTO`, `Space`, `Consumer`, `Location`. | `IdNotFoundException`; errores de usuario actual. |
| `POST /spaces` · ADMIN | `insertSpace` → `SpaceService.insertSpace` → resolver relaciones → `SpaceRepository.save`. La ubicación debe existir previamente. | `SpaceDTO`, `LocationDTO`, `Space`, `Consumer`, `Location`, `CancellationPolicies`, `EPolicyType`. | Validación DTO; `IdNotFoundException`, `InvalidDataException`, `IllegalArgumentException` si el enum de política es inválido. |
| `POST /spaces/ownedspace` · CLIENT/ADMIN | `insertOwnedSpace` → `SpaceService.insertOwnedSpace` → **usuario actual** y `ConsumerService.findById` → `ConsumerRepository.findById`; busca política mediante `CancellationPoliciesService` → `CancellationPoliciesRepository.findByType`; `SpaceRepository.save` persiste ubicación nueva y servicios en cascada. Devuelve `idSpace`. | `SpaceDTO`, `LocationDTO`, `ServiceItemDTO`, `Space`, `Consumer`, `Location`, `CancellationPolicies`, `EPolicyType`, `SpaceServiceItem`. | Validación DTO; `IdNotFoundException`, `InvalidDataException`, `IllegalArgumentException`; errores de usuario actual. |
| `PUT /spaces/{id}` · ADMIN | `modifySpace` → `findByIdForUpdate` → `applyEditableDetails`; actualiza la entidad administrada en una transacción. | `SpaceDTO`, `Space`, `Location`, `CancellationPolicies`; conserva propietario, actividad, publicación y servicios. | `IdNotFoundException`, `InvalidDataException`. |
| `PUT /spaces/ownedspace/{id}` · CLIENT/ADMIN, dueño | `modifyOwnedSpace` → usuario actual → `findByIdForUpdate` → verifica dueño almacenado → `applyEditableDetails`. | `SpaceDTO`, `Space`, `Location`, `CancellationPolicies`. | `IdNotFoundException`, `InvalidDataException`, `AccessDeniedException`. |
| `DELETE /spaces/{id}` · ADMIN | `deleteAnySpaceById` → `SpaceService.deleteById` → `SpaceRepository.existsById/findById/save`. Baja lógica. | `Space`. | `IdNotFoundException`. |
| `DELETE /spaces/ownedspace/{id}` · CLIENT/ADMIN, dueño | `deleteOwnedSpace` → `SpaceService.deleteOwnedSpace` → **usuario actual** → `SpaceRepository.findById` → comprueba dueño → `deleteById` → `SpaceRepository.existsById/findById/save`. | `Space`, `Consumer`. | `IdNotFoundException`, `InvalidDataException`; errores de usuario actual. |

La edición conserva la entidad y sus relaciones. El propietario es opcional en el DTO de edición; si se envía otro, se rechaza. Los cambios de catálogo usan sus rutas específicas.

Las lecturas de espacios de la tabla anterior se convierten con `PublicSpaceMapper.toDto` a `SpaceResponseDTO`. Ninguna devuelve la entidad directamente. `consumerOwner` solo contiene ID, nombre y apellido.

## Imágenes — `SpaceImageController`

Se guardan datos y URL de la imagen; no se sube un archivo binario. Las escrituras requieren JWT y el controlador comprueba al dueño del espacio o el rol ADMIN. Al editar se comprueba tanto la imagen existente como el espacio de destino.

| Llamado y acceso | Recorrido y resultado | Objetos de model | Excepciones / errores |
| --- | --- | --- | --- |
| `GET /spaceimages` · Público | `listSpaceImages` → `SpaceImageService.findAll` → `SpaceImageRepository.findAll`. | `SpaceImage`. | — |
| `GET /spaceimages/{id}` · Público | `findSpaceImageById` → `SpaceImageService.findById` → `SpaceImageRepository.findById`. | `SpaceImage`. | `IdNotFoundException`. |
| `GET /spaceimages/byspaceid/{id}` · Público | `findAllBySpaceId` → `SpaceImageService.findAllBySpaceId` → `SpaceService.existsById` → `SpaceRepository.existsByIdSpaceAndIsActiveTrue` → `SpaceImageRepository.findAllBySpaceIdSpace`. | `SpaceImage`, referencia a `Space`. | `IdNotFoundException` si el espacio no existe o está inactivo. |
| `POST /spaceimages` · Dueño o ADMIN | `insertSpace` → `SpaceImageService.insertSpaceImage` → `SpaceService.findById` → `SpaceRepository.findById` → `SpaceImageRepository.save`. | `SpaceImageDTO`, `SpaceImage`, `Space`. | Validación DTO; `InvalidDataException`, `IdNotFoundException`. |
| `PUT /spaceimages/{id}` · Dueño o ADMIN | `modifySpaceImage` → `SpaceImageService.modifySpaceImage` → `SpaceImageRepository.existsById`; resuelve espacio con `SpaceService.findById` → `SpaceRepository.findById` → `SpaceImageRepository.save`. | `SpaceImageDTO`, `SpaceImage`, `Space`. | Validación DTO; `IdNotFoundException`, `InvalidDataException`. |
| `DELETE /spaceimages/{id}` · Dueño o ADMIN | `deleteSapceImageById` → `SpaceImageService.deleteById` → `SpaceImageRepository.existsById/deleteById`. Borrado físico. | `SpaceImage` como registro eliminado. | `IdNotFoundException`. |

Las lecturas de imágenes se convierten con `PublicSpaceMapper.toDto` a `SpaceImageResponseDTO`, incluido un DTO seguro para el espacio anidado.

## Servicios del espacio — `SpaceServiceItemController`

Todos requieren JWT. El controlador consulta el rol cargado desde el token: ADMIN usa la variante general; los demás usuarios usan la variante de propietario/consumidor. Estas últimas resuelven el **usuario actual** y el espacio mediante `SpaceService.findById` → `SpaceRepository.findById`.

| Llamado | Recorrido y resultado | Objetos de model | Excepciones / errores |
| --- | --- | --- | --- |
| `GET /services/space/{idSpace}` | `listServicesFromSpace` → `SpaceServiceItemService.listOfServicesFromSpace` (ADMIN) o `ConsumerlistOfServicesFromSpace` → `SpaceServiceItemRepository.findAllSpaceServicesBySpaceId`. La variante consumidor consulta dueño/estado del espacio. | `SpaceServiceItem`, `SpaceServiceItemDTO`; `Space` y `Consumer` en variante consumidor. | ADMIN: —. Consumidor: `IdNotFoundException` y errores de usuario actual. |
| `POST /services/insert` | `insertServiceItem` → `SpaceServiceItemService.insertServiceItem` o `insertServiceItemOwner` → `SpaceService.findById` → `SpaceRepository.findById` → `SpaceServiceItemMapper.toEntity` → `SpaceServiceItemRepository.save`. | `SpaceServiceItemDTO`, `SpaceServiceItem`, `Space`; `Consumer` en variante dueño. | Validación DTO; `InvalidDataException`, `IdNotFoundException`; errores de usuario actual en variante dueño. |
| `PUT /services/update/{id}` | `updateServiceItem` → `SpaceServiceItemService.updateServiceItem` o `updateServiceItemOwner` → `SpaceServiceItemRepository.existsServiceItemInSpace`; resuelve espacio → `SpaceRepository.findById`; mapea → `SpaceServiceItemRepository.save`. | `SpaceServiceItemDTO`, `SpaceServiceItem`, `Space`; `Consumer` en variante dueño. | Validación DTO; `IdNotFoundException`, `InvalidDataException`; errores de usuario actual en variante dueño. |
| `DELETE /services/space/{idSpace}/delete/{id}` | `deleteServiceFromSpace` → `SpaceServiceItemService.deleteServiceItem` o `deleteServiceItemOwner` → `SpaceServiceItemRepository.existsServiceItemInSpace/deleteById`. La variante dueño también consulta espacio y usuario actual. Borrado físico. | `SpaceServiceItem`; `Space` y `Consumer` en variante dueño. | `IdNotFoundException`; `InvalidDataException` y errores de usuario actual en variante dueño. |

La consulta del repositorio ya excluye servicios inactivos, incluso para ADMIN y para el dueño. El resto de usuarios también filtra espacios inactivos.

## Reservas — `ReservationController`

Todos los llamados requieren JWT; se indican los requisitos adicionales cuando existen. `ReservationMapper` transforma `ReservationDTO` en `Reservation` para crear y modificar.

| Llamado y acceso adicional | Recorrido y resultado | Objetos de model | Excepciones / errores |
| --- | --- | --- | --- |
| `GET /reservations` · ADMIN | `findAll` → `ReservationService.findAll` → `ReservationRepository.findAll`. Lista global. | `Reservation`, con `Consumer`, `Space` y `ServiceSelected` asociados. | — |
| `GET /reservations/me` · CLIENT/ADMIN | `findAllForCurrentConsumer` → `ReservationService.findAllForLoggedConsumer` → **usuario actual** → `ReservationRepository.findAllByConsumer_IdConsumer`. | `Reservation`, `Consumer`, `Space`, `ServiceSelected`. | Errores de usuario actual. |
| `GET /reservations/{id}` · Cliente titular, dueño o ADMIN | `findById` → `ReservationService.findById` → `ReservationRepository.findById`. | `Reservation`, `Consumer`, `Space`, `ServiceSelected`. | `IdNotFoundException`. |
| `POST /reservations` · CLIENT/ADMIN | `createReservation` comprueba Bearer y `JwtUtil.extraerConsumerId` → `ReservationService.create` → validaciones y consultas descritas debajo → `ReservationRepository.save` → `NotificationService.createNotification` → `NotificationRepository.save`. | `ReservationDTO`, `Reservation`, `ReservationStatus`, `Consumer`, `Space`, `SpaceServiceItem`, `ServiceSelected`, `Notification`. | Validación DTO; `InvalidDateException`, `InvalidReservationException`, `IdNotFoundException`, `SelfReservationException`, `ReservationLimitException`, `ServiceOutOfPlaceException`; errores de usuario actual. El controlador devuelve 401 sin Bearer o 400 si falta `consumerId`. |
| `PUT /reservations` · Cliente titular o ADMIN | `ReservationService.modify` → bloquea reserva y espacios → revalida reglas → actualiza detalles y colección de servicios → recalcula y guarda. | `ReservationDTO`, `Reservation`, `SpaceServiceItem`, `ServiceSelected`. | Validación DTO; `InvalidDateException`, `IdNotFoundException`, `InvalidReservationException`, `SelfReservationException`, `ReservationLimitException`, `ServiceOutOfPlaceException`. |
| `PUT /reservations/confirm/{id}` · Dueño o ADMIN | `confirmReservation` → `ReservationService.confirmReservation` → `ReservationRepository.findById/save`; `NotificationService.createNotification` → `NotificationRepository.save`. Confirma y notifica al cliente. | `Reservation`, `ReservationStatus.CONFIRMED`, `Consumer`, `Space`, `Notification`. | `IdNotFoundException`. |
| `PUT /reservations/reject/{id}` · Dueño o ADMIN | `rejectReservation` → `ReservationService.rejectReservation` → `ReservationRepository.findById/save`; `NotificationService.createNotification` → `NotificationRepository.save`. Rechaza y notifica al cliente. | `Reservation`, `ReservationStatus.REJECTED`, `Consumer`, `Space`, `Notification`. | `IdNotFoundException`. |
| `PUT /reservations/cancel/{id}` · Cliente titular, dueño o ADMIN | `cancelReservation` → `ReservationService.cancelReservation` → `ReservationRepository.findById/save`; `NotificationService.createNotification` → `NotificationRepository.save`. Cancela y notifica al dueño. | `Reservation`, `ReservationStatus.CANCELLED`, `Consumer`, `Space`, `Notification`. | `IdNotFoundException`. |
| `PUT /reservations/complete/{id}` · Dueño o ADMIN | `completeReservation` → `ReservationService.completeReservation` → `ReservationRepository.findById/save`. | `Reservation`, `ReservationStatus.COMPLETED`. | `IdNotFoundException`. |
| `DELETE /reservations/{id}` · ADMIN | `softDelete` → `ReservationService.softDelete` → `ReservationRepository.findById/save`. Marca inactiva la reserva. | `Reservation`. | `IdNotFoundException`. |
| `POST /reservations/{id}/checkout` · CLIENT titular de la reserva | `SecurityUtils.isReservationOwner` → `ReservationRepository.findById` comprueba titular antes del controlador; `createPaymentPreference` → `ReservationService.findById` → `ReservationRepository.findById` → `IPaymentService.createPreference` (`PaymentServiceImpl`) → Mercado Pago. Devuelve `initPoint`; no guarda un pago local. | `Reservation`, `ReservationStatus`, `Space`, `Consumer`, `Credential`. Los objetos de preferencia son del SDK externo. | Reserva no confirmada: 400 directo. `MPException` / `MPApiException`: capturadas como 500; otras excepciones dentro del método, incluida `IdNotFoundException`, también se capturan como 500. La comprobación previa de permisos queda fuera de ese bloque. |

**Detalle breve de la creación:** abre una transacción `READ_COMMITTED` y bloquea el espacio con `SpaceService.findByIdForUpdate` → `SpaceRepository.findByIdForUpdate`; resuelve el **usuario actual**; consulta `ReservationRepository.findAllBySpace_IdSpace` y `SpaceService.findById` → `SpaceRepository.findById` para verificar disponibilidad y separación en minutos; obtiene el cliente con `ConsumerService.findById` → `ConsumerRepository.findById`; comprueba el límite con `ReservationRepository.countCompletedReservationsByConsumerAndSpace`; carga opcionales desde `SpaceServiceItemRepository.findById`. Calcula duración × precio base + servicios, fija `TENTATIVE` y guarda los `ServiceSelected` en cascada con la reserva. El bloqueo del espacio se libera al confirmar o revertir la transacción, que incluye reserva, opcionales y notificación. El cliente efectivo se obtiene de la sesión, aunque el controlador también exija el claim `consumerId`.

La edición no repite todas las reglas de creación: no comprueba solapamiento, límite ni autorreserva. Los controladores verifican los permisos de consulta y modificación sobre la reserva almacenada. El cliente no puede transferir su reserva a otro consumidor, y la edición general no cambia estado ni actividad. Cancelar no llama a reembolsos ni calcula penalizaciones.

## Servicios seleccionados en reservas — `ServiceSelectedController`

Todos requieren JWT. La lectura permite al cliente titular, al dueño del espacio y a ADMIN; insertar o eliminar servicios seleccionados requiere ser el cliente titular o ADMIN. Los controles usan la reserva o el servicio seleccionado almacenados.

| Llamado | Recorrido y resultado | Objetos de model | Excepciones / errores |
| --- | --- | --- | --- |
| `GET /servicesselected/reservation/{idReservation}` | `getServicesSelected` → `ServiceSelectedService.getServicesSelectedOfReservation` → `ServiceSelectedRepository.findServiceSelectedByIdReservation`. Devuelve una proyección DTO. | `ServiceSelected`, `ServiceSelectedDTO`, referencia a `Reservation`. | —; no comprueba previamente la existencia de la reserva. |
| `POST /servicesselected/insert/list/{idReservation}` | Bloquea reserva → exige edición permitida → carga y valida servicios del catálogo → agrega snapshots a la colección y recalcula total. Persistencia en cascada. | Lista de `SelectServiceDTO` con `idService`, `SpaceServiceItem`, `ServiceSelected`, `Reservation`. | `IdNotFoundException`, `InvalidReservationException`; entrada inválida devuelve 400. |
| `DELETE /servicesselected/delete/{id}` | Carga selección → bloquea reserva → exige edición permitida → quita de la colección → recalcula total. `orphanRemoval` elimina el registro. | `ServiceSelected`, `Reservation`. | `IdNotFoundException`, `InvalidReservationException`. |

La inserción directa usa precio y descripción recibidos en el DTO; no consulta el catálogo `SpaceServiceItem`. Estas inserciones/borrados no recalculan el precio final de la reserva.

## Comentarios — `CommentController`

Las lecturas convierten `Comment` a `CommentDTO` mediante `CommentMapper`, que también toma el username de `Consumer.credentials` (`Credential`). En las escrituras, el rol del JWT elige la variante ADMIN o consumidor; cuando se guarda un comentario, el autor se toma del **usuario actual**.

| Llamado y acceso | Recorrido y resultado | Objetos de model | Excepciones / errores |
| --- | --- | --- | --- |
| `GET /comments` · JWT | `listAllComments` → `CommentService.findAll` → `CommentRepository.findAll` → mapper. | `Comment`, `CommentDTO`, `Consumer`, `Credential`, `Space`. | — |
| `GET /comments/{id}` · JWT | `findCommentById` → `CommentService.findById` → `CommentRepository.findById` → mapper. | `Comment`, `CommentDTO`, `Consumer`, `Credential`, `Space`. | `IdNotFoundException`. |
| `GET /comments/byspaceid/{id}` · Público | `findAllBySpaceId` → `CommentService.findAllBySpaceId` → `SpaceService.existsById` → `SpaceRepository.existsByIdSpaceAndIsActiveTrue` → `CommentRepository.findAllBySpaceIdSpace` → mapper. | `Comment`, `CommentDTO`, `Space`, `Consumer`, `Credential`. | `IdNotFoundException` si el espacio no existe o está inactivo. |
| `GET /comments/byconsumerid/{id}` · JWT | `findAllByConsumerId` → `CommentService.findAllByConsumerId` → `ConsumerService.existsById` → `ConsumerRepository.existsById` → `CommentRepository.findAllByConsumerIdConsumer` → mapper. Solo comentarios de espacios activos. | `Comment`, `CommentDTO`, `Consumer`, `Credential`, `Space`. | `IdNotFoundException`. |
| `GET /comments/byscore/asc` · JWT | `findAllByScoreASC` → `CommentService.filterByScoreASC` → `CommentRepository.findAllByOrderByScoreAsc` → mapper. | `Comment`, `CommentDTO`, `Consumer`, `Credential`, `Space`. | — |
| `GET /comments/byscore/desc` · JWT | `findAllByScoreDesc` → `CommentService.filterByScoreDESC` → `CommentRepository.findAllByOrderByScoreDesc` → mapper. | `Comment`, `CommentDTO`, `Consumer`, `Credential`, `Space`. | — |
| `POST /comments` · CLIENT/ADMIN | `insertComment` → `CommentService.insertComment` (ADMIN) o `consumerInsertCommentOnSpace` → **usuario actual**; para consumidor, `ReservationService.findByIdConsumer` → `ReservationRepository.findAllByConsumer_IdConsumer`; resuelve autor/espacio con `ConsumerService.findById` y `SpaceService.findById` → `ConsumerRepository.findById`, `SpaceRepository.findById` → `CommentRepository.save`. | `CommentDTO`, `Comment`, `Consumer`, `Space`; `Reservation` y `ReservationStatus` en variante consumidor. | `InvalidDataException`, `IdNotFoundException`; errores de usuario actual. |
| `PUT /comments/{id}` · JWT | `modifyComment` → `CommentService.modifyComment` (ADMIN) o `consumerModifyCommentOnSpace` → `CommentRepository.findById` → comprueba autor con **usuario actual**; `CommentRepository.existsById`; resuelve autor/espacio con `ConsumerService.findById` y `SpaceService.findById` → `ConsumerRepository.findById`, `SpaceRepository.findById` → `CommentRepository.save`. | `CommentDTO`, `Comment`, `Consumer`, `Space`. | `IdNotFoundException`, `InvalidDataException`, `AccessDeniedException`; errores de usuario actual. |
| `DELETE /comments/{id}` · CLIENT/ADMIN | `deleteCommentById` → `CommentService.deleteById` (ADMIN: `CommentRepository.existsById`) o `deleteByIdCustomer` (lee `CommentRepository.findById` y comprueba **usuario actual**) → `CommentRepository.deleteById`. | `Comment`; `Consumer` en variante consumidor. | `IdNotFoundException`; `AccessDeniedException` y errores de usuario actual en variante consumidor. |

Para crear como cliente se exige una reserva activa `CONFIRMED` o `COMPLETED` del espacio. En la edición de cliente, se carga el comentario desde `CommentRepository.findById` y se compara su autor guardado con el usuario actual; `idConsumer` del DTO no concede permisos.

## Notificaciones — `NotificationController`

El controlador transforma `Notification` en un mapa de respuesta: no existe un `NotificationDTO` en `model/records`.

| Llamado y acceso | Recorrido y resultado | Objetos de model | Excepciones / errores |
| --- | --- | --- | --- |
| `GET /notifications` · ADMIN | `listAll` → `NotificationService.listAll` → `NotificationRepository.findAll`. | `Notification`. | — |
| `GET /notifications/me` · CLIENT/ADMIN | `getMyNotifications` → `NotificationService.listAllByIdConsumerForConsumer` → **usuario actual** → `NotificationRepository.findByConsumer_IdConsumer`; por cada no vista, `markAsSeen` → `NotificationRepository.findById/save`. | `Notification`, `Consumer`. | `IdNotFoundException` al marcar; errores de usuario actual. |
| `GET /notifications/unread-count` · CLIENT/ADMIN | `getUnreadCount` → `NotificationService.countUnseenForConsumer` → **usuario actual** → `NotificationRepository.findByConsumer_IdConsumer`. Cuenta sin marcar vistas. | `Notification`, `Consumer`. | Errores de usuario actual. |
| `GET /notifications/consumer/{id}` · CLIENT/ADMIN | `findAllByIdConsumer` → ADMIN: `NotificationService.listAllByIdConsumer` → `NotificationRepository.findByConsumer_IdConsumer`; cliente: `listAllByIdConsumerForConsumer`, mismo recorrido que `/me`. | `Notification`, `Consumer`. | ADMIN: —. Cliente: `IdNotFoundException` al marcar y errores de usuario actual. |
| `GET /notifications/consumer/onlyunseen` · CLIENT | `findAllUnseenByIdconsumer` → `NotificationService.listAllUnseenForConsumer` → **usuario actual** → `NotificationRepository.findByConsumer_IdConsumer`; filtra no vistas y llama `markAsSeen` → `NotificationRepository.findById/save`. | `Notification`, `Consumer`. | `IdNotFoundException` al marcar; errores de usuario actual. |
| `GET /notifications/{id}` · Destinatario o ADMIN | `findById` → `NotificationService.findById` → `NotificationRepository.findById`. | `Notification`. | `IdNotFoundException`. |
| `POST /notifications/{id}` · Destinatario o ADMIN | `markAsSeen` → `NotificationService.markAsSeen` → `NotificationRepository.findById/save`. | `Notification`. | `IdNotFoundException`. |

Los listados propios marcan como vistas las notificaciones devueltas. En `/consumer/{id}`, un cliente usa su identidad JWT e ignora el ID solicitado; ADMIN sí usa ese ID. Las operaciones por ID de notificación comprueban que el destinatario sea el usuario autenticado, o que tenga rol ADMIN.

## Webhook de pagos — `PaymentWebhookController`

| Llamado y acceso | Recorrido y resultado | Objetos de model | Excepciones / errores |
| --- | --- | --- | --- |
| `POST /v1/payments/webhook` · JWT según configuración actual | `receiveWebhook` obtiene topic e ID → `IPaymentService.processNotification` (`PaymentServiceImpl`) → Mercado Pago → `PaymentRepository.findById`; `ReservationRepository.getReferenceById/findById/save` → `PaymentRepository.save`. Si el cuerpo contiene `isSimulation`, usa `processMockNotification`, sin consultar Mercado Pago y sin `getReferenceById`. Si no identifica un evento de pago con ID, responde 200 sin procesar. | `PaymentModel`, `Reservation`, `ReservationStatus`. Entrada: mapa; recurso de pago externo del SDK. | `IdNotFoundException`, errores del SDK y otras excepciones dentro del servicio se capturan y registran. Errores de conversión del cuerpo en el controlador, como `NumberFormatException` o `ClassCastException`, pueden propagarse. |

Un pago `approved` coloca la reserva en `CONFIRMED`; uno `rejected`, en `CANCELLED`. La simulación la confirma directamente. El servicio captura errores internos y el controlador puede responder 200 aunque el procesamiento haya fallado; fallos al finalizar la transacción todavía pueden propagarse. No hay verificación de firma del proveedor en este controlador, y la ruta no está exceptuada del JWT de la aplicación.

## Excepciones compartidas y respuesta HTTP

El [GlobalExceptionHandler](../backend/src/main/java/com/utn/space/venueaapi/exceptions/GlobalExceptionHandler.java) trata las excepciones que llegan desde los controladores y servicios de esta forma. Los errores capturados localmente, como login, checkout o procesamiento de pagos, siguen lo indicado en sus respectivas filas.

| Excepción | Uso en el recorrido | Respuesta del handler actual |
| --- | --- | --- |
| `AccessDeniedException` | El usuario no tiene permiso sobre el recurso solicitado. | 403 |
| `IdNotFoundException` | No se encuentra una entidad o relación por ID. | 404 |
| `NameNotFoundException` | No se encuentra el perfil por username. | 404 |
| `ServiceOutOfPlaceException` | Un servicio seleccionado pertenece a otro espacio. | 404 |
| `InvalidDateException` | Fechas de reserva inválidas. | 400 |
| `InvalidReservationException` | El espacio no está disponible en las fechas solicitadas. | 400 |
| `SelfReservationException` | El usuario intenta reservar su propio espacio. | 400 |
| `MethodArgumentNotValidException` | Fallan las restricciones activas de un DTO validado. | 400, mapa de errores por campo |
| `InvalidDataException` | Datos o condiciones de negocio inválidos, incluidas algunas comprobaciones de propiedad. | **502**, por handler genérico; no tiene handler específico. |
| `ReservationLimitException` | Se alcanzó el límite de cinco reservas confirmadas/completadas para usuario y espacio. | **502**, por handler genérico. |
| `IllegalArgumentException`, `IllegalStateException`, `RuntimeException` y otras sin handler específico | Argumentos inválidos, identidad no disponible u otros fallos propagados. | 502, por handler genérico |
| `IOException` | Errores de entrada/salida propagados. | 502 |
| `SpaceUnavailableException` | Existe la clase y su handler, pero ningún flujo actual la lanza explícitamente. | 400 si se lanzara |

Los errores de seguridad pueden impedir que se llegue al controlador. Las denegaciones de permisos del método tienen un handler específico que responde 403.

## Componentes sin endpoint propio

`LocationService`, `CancellationPoliciesService` y `CredentialService` participan a través de los controladores anteriores; no tienen controladores dedicados. `RefundService.refundPayment` consulta `PaymentRepository`, llama a Mercado Pago y guarda `PaymentRefund` con `PaymentRefundRepository`, pero **ningún controlador lo invoca actualmente**. Por eso no forma parte del recorrido de cancelar reservas.

Las rutas de Swagger/OpenAPI son infraestructura de documentación. `SecurityConfig` también menciona `/api/spaces/search`, pero no hay un método de controlador que implemente esa ruta como búsqueda.

Fuentes del panorama: [controladores](../backend/src/main/java/com/utn/space/venueaapi/controllers), [servicios](../backend/src/main/java/com/utn/space/venueaapi/service), [repositorios](../backend/src/main/java/com/utn/space/venueaapi/repository), [modelos](../backend/src/main/java/com/utn/space/venueaapi/model) y [configuración de seguridad](../backend/src/main/java/com/utn/space/venueaapi/config/SecurityConfig.java).
