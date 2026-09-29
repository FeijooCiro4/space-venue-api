# Guía de los archivos de `main`

Esta guía explica para qué sirve cada archivo y cómo se relaciona con el resto del proyecto. La primera parte cubre los **10 archivos de `controllers`**. Las demás carpetas de `main` quedan para próximas ampliaciones.

Las explicaciones describen el código actual. Los ejemplos usan situaciones e identificadores ficticios. Este archivo es documentación de lectura: no configura el funcionamiento de la aplicación.

## 1. ¿Qué hace un controlador?

Imaginá la aplicación como un lugar con una recepción y distintos equipos de trabajo. El **controlador es la recepción**: recibe una solicitud, identifica qué se necesita, llama al servicio correspondiente y devuelve una respuesta.

Por ejemplo, ante una solicitud para reservar un salón:

```text
Solicitud HTTP: crear una reserva
    ↓
Seguridad: comprobar el acceso a la operación
    ↓
ReservationController: recibir los datos y llamar al servicio
    ↓
ReservationService: aplicar las reglas de la reserva
    ↓
Repository: consultar o guardar en la base de datos
    ↓
Respuesta HTTP: resultado o error
```

El recorrido es una simplificación: la seguridad también puede comprobar permisos al entrar en un método. Además, algunos controladores actuales, especialmente `AuthController` y `ConsumerController`, realizan parte del trabajo directamente, como preparar usuarios o comprobar datos.

### Palabras que vas a encontrar

| Concepto | Explicación sencilla |
| --- | --- |
| Endpoint | Una operación de la API, identificada por un método HTTP y una ruta. `GET /api/spaces` y `POST /api/spaces` son operaciones distintas. |
| GET | Se utiliza para consultar información. Más adelante se señala una particularidad de las notificaciones que también modifica su estado. |
| POST | Se utiliza para crear datos o ejecutar una acción, como iniciar sesión. |
| PUT | Se utiliza para modificar datos o cambiar estados. |
| DELETE | Solicita una eliminación. Puede ser una baja lógica: desactivar un registro conservándolo en la base de datos. |
| DTO | Objeto que agrupa los datos recibidos o devueltos por una operación. Por ejemplo, `SpaceDTO` reúne datos de un espacio. |
| Entidad | Objeto del modelo relacionado con la persistencia, como `Space` o `Reservation`. Algunos controladores devuelven entidades directamente y otros utilizan DTO. |
| Servicio | Clase que realiza operaciones y aplica reglas del negocio. Por ejemplo, comprobar las condiciones de una reserva. |
| `ResponseEntity` | Permite elegir el estado HTTP y el contenido de la respuesta. Por ejemplo, `201` al crear un recurso o `400` ante ciertos datos incorrectos. |
| JWT | Token firmado que permite identificar al usuario en solicitudes posteriores al inicio de sesión. Se envía en el encabezado `Authorization: Bearer ...`. |

### Cómo leer las anotaciones

| Anotación o parámetro | Qué significa en estos archivos |
| --- | --- |
| `@RestController` | Declara una clase que atiende solicitudes HTTP y devuelve datos o texto. |
| `@RequestMapping` | Define la ruta base. Las rutas de los métodos se agregan a ella. |
| `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping` | Conectan un método Java con una operación HTTP. |
| `@PathVariable` | Lee un dato de la ruta. En `/api/spaces/7`, el `7` puede ser el identificador del espacio. |
| `@RequestParam` | Lee un parámetro de consulta, como `?active=false`. |
| `@RequestBody` | Lee el cuerpo de la solicitud, normalmente JSON. |
| `@Validated` | Activa las validaciones correspondientes al grupo indicado, como creación o modificación. Las reglas concretas están en el DTO. |
| `@PreAuthorize` | Comprueba una condición de acceso antes de ejecutar el método, como tener el rol `ADMIN`. |
| `Principal` / `Authentication` | Dan acceso a la identidad autenticada y, en el segundo caso, a sus autoridades o roles. |
| `@Autowired` / constructores | Permiten recibir dependencias administradas por Spring. Lombok también genera constructores mediante `@RequiredArgsConstructor` o `@AllArgsConstructor`. |
| `@Operation`, `@Tag`, `@ApiResponse` | Describen operaciones para la documentación de la API. No implementan sus reglas ni sus respuestas. |

**Sobre los permisos:** también interviene [SecurityConfig.java](../java/com/utn/space/venueaapi/config/SecurityConfig.java). Que un método no tenga `@PreAuthorize` no significa que sea público. Del mismo modo, exigir el rol `CLIENT` no demuestra por sí solo que el recurso pertenezca a ese usuario; hay que seguir las comprobaciones del método y del servicio.

## 2. Mapa de la carpeta `controllers`

| Archivo | Responsabilidad principal |
| --- | --- |
| [AuthController.java](../java/com/utn/space/venueaapi/controllers/AuthController.java) | Registrar cuentas, iniciar sesión y cerrar sesión. |
| [ConsumerController.java](../java/com/utn/space/venueaapi/controllers/ConsumerController.java) | Consultar y administrar usuarios y sus perfiles. |
| [SpaceController.java](../java/com/utn/space/venueaapi/controllers/SpaceController.java) | Publicar, consultar, buscar y administrar espacios. |
| [SpaceImageController.java](../java/com/utn/space/venueaapi/controllers/SpaceImageController.java) | Administrar los registros de imágenes asociados a espacios. |
| [SpaceServiceItemController.java](../java/com/utn/space/venueaapi/controllers/SpaceServiceItemController.java) | Administrar los servicios adicionales que ofrece un espacio. |
| [ReservationController.java](../java/com/utn/space/venueaapi/controllers/ReservationController.java) | Crear y administrar reservas e iniciar su pago. |
| [ServiceSelectedController.java](../java/com/utn/space/venueaapi/controllers/ServiceSelectedController.java) | Administrar los adicionales seleccionados para una reserva concreta. |
| [PaymentWebhookController.java](../java/com/utn/space/venueaapi/controllers/PaymentWebhookController.java) | Recibir avisos del proveedor de pagos y delegar su procesamiento. |
| [NotificationController.java](../java/com/utn/space/venueaapi/controllers/NotificationController.java) | Consultar avisos internos y gestionar su estado de lectura. |
| [CommentController.java](../java/com/utn/space/venueaapi/controllers/CommentController.java) | Administrar comentarios y puntuaciones sobre los espacios. |

## 3. `AuthController.java`: acceso a la aplicación

**Responsabilidad:** atender el registro, el inicio de sesión y la invalidación del token al cerrar sesión.

**Ruta base:** `/api/auth`.

| Operación | Qué hace |
| --- | --- |
| `POST /api/auth/register` | Recibe un `RegistroDTO`, comprueba usuario y contraseña, verifica si el nombre de usuario ya existe y crea credenciales y perfil con rol `CLIENT`. |
| `POST /api/auth/login` | Comprueba usuario y contraseña mediante `AuthenticationManager` y genera un JWT con `JwtUtil`. |
| `POST /api/auth/logout` | Toma el token del encabezado y utiliza `TokenBlacklistService` para invalidarlo durante el tiempo de vigencia restante. |

**Con quién trabaja:** `CredentialService` y `ConsumerService` para los datos de la cuenta; `PasswordEncoder` para transformar la contraseña en un hash; `AuthenticationManager`, `JwtUtil` y `TokenBlacklistService` para el acceso.

**Ejemplo:** Ana registra una cuenta, inicia sesión y recibe un token. En sus siguientes solicitudes incluye ese token para identificarse. Al cerrar sesión, el token se incorpora a la lista de tokens invalidados.

**Detalle para entender el código:** el inicio de sesión devuelve texto con el formato `Bearer <token>`, no un objeto JSON con un campo `token`. El hash de la contraseña permite comprobarla sin guardarla como texto legible. El JWT identifica la sesión de acceso; no es la contraseña.

## 4. `ConsumerController.java`: usuarios y perfiles

**Responsabilidad:** exponer operaciones sobre usuarios registrados y permitir que una persona modifique o desactive su propia cuenta.

**Ruta base:** `/api`. En este archivo conviven rutas con `usuarios` y con `usuario`.

| Operación | Qué hace |
| --- | --- |
| `POST /api/usuarios` | Ofrece otra vía de creación de cuenta: recibe un `Credential` y crea un perfil con sus campos personales vacíos. |
| `GET /api/usuarios` | Lista usuarios para administración. |
| `GET /api/usuarios/{id}` | Consulta un usuario por identificador. |
| `GET` o `POST /api/usuarios/byfields` | Busca usuarios con un `ConsumerFilterDTO` enviado en el cuerpo. |
| `PUT /api/usuarios/{id}/status` | Tiene declarada una operación para cambiar el estado mediante el parámetro `active`, pero actualmente solo devuelve un mensaje. |
| `DELETE /api/usuarios/{id}` | Solicita la baja lógica de un usuario desde administración. |
| `PUT /api/usuario` | Actualiza los datos personales del usuario autenticado. |
| `DELETE /api/usuario` | Solicita la baja lógica de la cuenta autenticada. |

**Con quién trabaja:** principalmente `ConsumerService`; también `CredentialService` y `PasswordEncoder` en la creación de cuentas.

**Ejemplo:** Ana quiere cambiar su teléfono. Envía el nuevo dato a `PUT /api/usuario`. El controlador obtiene su identidad de `Principal`, busca su perfil, comprueba duplicados de correo o teléfono cuando corresponde y guarda los cambios.

**Distinción útil:** `AuthController` se concentra en el acceso; `ConsumerController`, en la administración de la cuenta y el perfil. Las dos rutas de registro existen, pero reciben datos distintos. Las rutas singulares `/usuario` trabajan con la identidad autenticada; las plurales permiten operaciones generales o por identificador.

**Comportamiento actual:** `toggleUserStatus` no cambia nada en la base de datos, aunque responda «Estado del usuario actualizado». Su implementación está pendiente.

## 5. `SpaceController.java`: catálogo y administración de espacios

**Responsabilidad:** permitir la publicación, consulta, búsqueda, modificación y baja de espacios reservables.

**Ruta base:** `/api/spaces`.

| Operación | Qué hace |
| --- | --- |
| `GET /api/spaces` | Lista espacios activos del catálogo. |
| `GET /api/spaces/{id}` | Consulta un espacio concreto. |
| `POST /api/spaces/byfields` | Busca espacios activos mediante un `SpaceFilterDTO`. |
| `GET /api/spaces/showinactives` | Lista espacios incluyendo inactivos, con acceso administrativo. |
| `GET` o `POST /api/spaces/byfields/showinactives` | Busca incluyendo inactivos, con acceso administrativo y filtros en el cuerpo. |
| `GET /api/spaces/ownedspaces` | Lista los espacios del usuario autenticado, incluyendo inactivos. |
| `GET` o `POST /api/spaces/ownedspaces/byfields` | Filtra los espacios propios con datos enviados en el cuerpo. |
| `POST /api/spaces/ownedspace` | Crea un espacio para el usuario autenticado. |
| `PUT` o `DELETE /api/spaces/ownedspace/{id}` | Modifica o solicita la baja de un espacio propio. |
| `POST /api/spaces` | Crea un espacio desde administración. |
| `PUT` o `DELETE /api/spaces/{id}` | Modifica o solicita la baja de un espacio desde administración. |

**Con quién trabaja:** `SpaceService`. Recibe `SpaceDTO` para escritura y `SpaceFilterDTO` para búsquedas; devuelve espacios, listas, mensajes o el identificador creado según la operación.

**Ejemplo:** una persona publica un salón mediante `/ownedspace`. La respuesta incluye `idSpace`, que permite después asociarle imágenes y servicios adicionales.

**Distinción útil:** un espacio es el lugar ofrecido; una reserva es una solicitud de uso de ese lugar durante un período. Que el catálogo muestre un espacio activo no significa que esté libre para cualquier fecha. Las condiciones de una reserva se revisan en el flujo de reservas.

## 6. `SpaceImageController.java`: imágenes asociadas a un espacio

**Responsabilidad:** administrar los datos que relacionan imágenes con espacios.

**Ruta base:** `/api/spaceimages`.

| Operación | Qué hace |
| --- | --- |
| `GET /api/spaceimages` | Lista registros de imágenes. |
| `GET /api/spaceimages/{id}` | Consulta una imagen por su identificador. |
| `GET /api/spaceimages/byspaceid/{id}` | Lista las imágenes de un espacio; aquí el identificador corresponde al espacio. |
| `POST /api/spaceimages` | Crea un registro a partir de un `SpaceImageDTO`. |
| `PUT /api/spaceimages/{id}` | Modifica los datos de una imagen. |
| `DELETE /api/spaceimages/{id}` | Delega la eliminación del registro al servicio. |

**Con quién trabaja:** `SpaceImageService`. Las consultas devuelven entidades `SpaceImage`; las operaciones de escritura no incluyen un cuerpo de respuesta.

**Ejemplo:** al espacio 7 se le asocia una foto indicando su URL, nombre de archivo y los demás datos del DTO. Luego `/byspaceid/7` permite recuperar las imágenes asociadas a ese espacio.

**Detalle para entender el código:** estos endpoints reciben metadatos y una URL mediante JSON. No reciben el archivo binario mediante una carga `multipart`. Además, el método llamado `insertSpace` de este archivo crea un registro de imagen: su nombre puede confundirse con la creación del espacio.

## 7. `SpaceServiceItemController.java`: adicionales ofrecidos

**Responsabilidad:** administrar el catálogo de servicios adicionales de cada espacio, como un proyector o un equipo de sonido.

**Ruta base:** `/api/services`.

| Operación | Qué hace |
| --- | --- |
| `GET /api/services/space/{idSpace}` | Consulta los adicionales de un espacio. |
| `POST /api/services/insert` | Crea un adicional a partir de un `SpaceServiceItemDTO`. |
| `PUT /api/services/update/{id}` | Modifica un adicional. |
| `DELETE /api/services/space/{idSpace}/delete/{id}` | Delega la eliminación de un adicional del espacio indicado. |

**Con quién trabaja:** `SpaceServiceItemService`. El controlador examina `Authentication` y elige métodos del servicio diferentes para administradores y para los demás usuarios. El DTO contiene datos como descripción, precio, estado e identificador del espacio.

**Ejemplo:** el dueño del salón agrega «Proyector» con un precio de 250. A partir de ese momento existe un adicional ofrecido por el espacio. Eso todavía no significa que una reserva lo haya seleccionado.

**Distinción útil:** aquí «servicio» significa una prestación comercial adicional. La clase Java `SpaceServiceItemService`, en cambio, es un servicio de la capa de negocio que administra esas prestaciones.

## 8. `ReservationController.java`: reservas y comienzo del pago

**Responsabilidad:** recibir solicitudes de reserva, permitir su consulta y modificación, cambiar estados e iniciar el proceso de pago.

**Ruta base:** `/api/reservations`.

| Operación | Qué hace |
| --- | --- |
| `GET /api/reservations` | Consulta la lista de reservas. |
| `GET /api/reservations/me` | Consulta las reservas del usuario autenticado. |
| `GET /api/reservations/{id}` | Consulta una reserva por identificador. |
| `POST /api/reservations` | Recibe un `ReservationDTO`, comprueba datos del token y delega la creación. Devuelve la reserva con estado HTTP `201`. |
| `PUT /api/reservations` | Modifica una reserva indicada mediante el DTO del cuerpo. |
| `PUT /api/reservations/confirm/{id}` | Solicita pasar a `CONFIRMED`: confirmada. |
| `PUT /api/reservations/reject/{id}` | Solicita pasar a `REJECTED`: rechazada. |
| `PUT /api/reservations/complete/{id}` | Solicita pasar a `COMPLETED`: completada. |
| `PUT /api/reservations/cancel/{id}` | Solicita pasar a `CANCELLED`: cancelada. |
| `DELETE /api/reservations/{id}` | Solicita una baja lógica, reservada al administrador. |
| `POST /api/reservations/{id}/checkout` | Solicita al servicio de pagos una preferencia y devuelve la URL en el campo `initPoint`. |

**Con quién trabaja:** `ReservationService` para las operaciones de reserva, `JwtUtil` para leer información del token e `IPaymentService` para iniciar el pago. También recibe `ConsumerService` en el constructor, aunque actualmente no lo utiliza en sus métodos.

**Ejemplo:** Ana solicita el salón para un período determinado. El controlador recibe la solicitud; `ReservationService` comprueba condiciones como las fechas y los solapamientos, calcula el importe y guarda la reserva inicialmente como tentativa.

**Distinción útil:** confirmar una reserva y pagarla son pasos diferentes. El checkout exige que la reserva esté `CONFIRMED` y comprueba que quien lo solicita tenga el rol `CLIENT` y sea su titular. Obtener `initPoint` inicia el proceso de pago; el aviso posterior se recibe en `PaymentWebhookController`.

Los métodos de cambio de estado delegan las reglas en el servicio. Sus anotaciones de rol deben leerse junto con las comprobaciones internas para entender quién puede modificar cada reserva.

## 9. `ServiceSelectedController.java`: adicionales de una reserva

**Responsabilidad:** consultar, agregar y quitar los servicios adicionales que quedaron asociados a una reserva concreta.

**Ruta base:** `/api/servicesselected`.

| Operación | Qué hace |
| --- | --- |
| `GET /api/servicesselected/reservation/{idReservation}` | Devuelve los adicionales seleccionados de una reserva como lista de `ServiceSelectedDTO`. |
| `POST /api/servicesselected/insert/list/{idReservation}` | Recibe una lista de `SelectServiceDTO` (`idService` del catálogo), congela sus datos y recalcula el total de la reserva. |
| `DELETE /api/servicesselected/delete/{id}` | Elimina un registro de selección por su identificador y recalcula el total de la reserva. |

**Con quién trabaja:** `ServiceSelectedService`. Entre los datos de selección aparecen `descriptionFrozen` y `priceAtReservation`: descripción y precio registrados para esa reserva.

**Ejemplo:** el salón ofrece un proyector a 250 y Ana lo selecciona para su reserva. El catálogo del proyector pertenece a `SpaceServiceItemController`; el registro de lo contratado por Ana pertenece a `ServiceSelectedController`. Los campos de descripción y precio propios permiten conservar esa información aunque posteriormente cambie la oferta del catálogo.

**Detalle para entender el código:** el POST recibe un arreglo JSON de selecciones, no un único objeto de reserva. El identificador utilizado en DELETE es el de la selección, no el del espacio ni el de la reserva.

## 10. `PaymentWebhookController.java`: avisos de pagos

**Responsabilidad:** recibir una notificación HTTP sobre un pago y encargar su procesamiento al servicio de pagos. Este tipo de aviso automático se llama *webhook*.

**Ruta:** `POST /api/v1/payments/webhook`.

**Cómo trabaja:** recibe parámetros de consulta `topic` e `id` o datos en el cuerpo. Interpreta campos como `type`, `action` y `data.id`. Cuando reconoce el tema `payment` y tiene un identificador, llama a `IPaymentService.processNotification` o al método de simulación. Al terminar normalmente responde `200` sin cuerpo, incluso si no reconoció un evento para procesar.

**Con quién trabaja:** `IPaymentService`, cuya implementación es `PaymentServiceImpl`. El controlador interpreta la entrada; el servicio realiza el procesamiento del pago.

**Ejemplo de cuerpo que reconoce:**

```json
{
  "type": "payment",
  "data": {
    "id": "123456"
  }
}
```

Ese número identifica el pago. El aviso permite al servicio consultar y procesar su información.

**Distinción útil:** `ReservationController` solicita el comienzo del pago; `PaymentWebhookController` recibe novedades posteriores sobre ese pago.

**Particularidades actuales:** la presencia del campo `isSimulation` activa el procesamiento simulado, incluso si su valor es `false`. Además, `SecurityConfig` no declara pública esta ruta, por lo que actualmente requiere autenticación. Esta explicación describe el comportamiento existente, no presupone que la recepción externa esté configurada.

## 11. `NotificationController.java`: avisos internos

**Responsabilidad:** permitir consultar las notificaciones almacenadas y gestionar si ya fueron vistas.

**Ruta base:** `/api/notifications`.

| Operación | Qué hace |
| --- | --- |
| `GET /api/notifications` | Lista todas las notificaciones para administración. |
| `GET /api/notifications/me` | Consulta las notificaciones del usuario autenticado. |
| `GET /api/notifications/unread-count` | Devuelve la cantidad pendiente en un objeto como `{"count": 3}`. |
| `GET /api/notifications/consumer/{id}` | Para un administrador, consulta el usuario indicado; para un cliente, consulta su propia cuenta e ignora ese identificador. |
| `GET /api/notifications/consumer/onlyunseen` | Consulta las notificaciones pendientes del cliente autenticado. |
| `GET /api/notifications/{id}` | Consulta una notificación por identificador. |
| `POST /api/notifications/{id}` | Marca una notificación como vista. |

**Con quién trabaja:** `NotificationService`. Los avisos pueden crearse desde otros flujos del negocio, como el de reservas; este controlador no ofrece un endpoint para crearlos.

**Ejemplo:** una solicitud de reserva genera un aviso para el dueño del espacio. El dueño consulta sus notificaciones y puede conocer cuántas tiene pendientes.

**Detalle para entender el código:** el método privado `toDto` prepara la respuesta como un mapa; no es un endpoint. Incluye pares de campos equivalentes, como `idNotification` e `id`, o `isSeen` y `seen`, y convierte la fecha a milisegundos desde el inicio de la época Unix usando UTC.

**Comportamiento actual:** consultar `/me`, consultar `/consumer/{id}` como cliente o consultar `/consumer/onlyunseen` también marca como vistas las notificaciones recuperadas, por lo que esas lecturas cambian su estado. `/unread-count` solo cuenta y no las marca. Estas notificaciones son registros internos; este archivo no envía correos electrónicos.

## 12. `CommentController.java`: opiniones y puntuaciones

**Responsabilidad:** ofrecer operaciones para consultar y administrar los comentarios que los usuarios dejan sobre los espacios.

**Ruta base:** `/api/comments`.

| Operación | Qué hace |
| --- | --- |
| `GET /api/comments` | Lista comentarios como `CommentDTO`. |
| `GET /api/comments/{id}` | Consulta un comentario. |
| `POST /api/comments` | Recibe un `CommentDTO` y delega su creación. |
| `PUT /api/comments/{id}` | Modifica un comentario. |
| `DELETE /api/comments/{id}` | Delega la eliminación de un comentario. |
| `GET /api/comments/byspaceid/{id}` | Consulta comentarios de un espacio; esta consulta es pública. |
| `GET /api/comments/byconsumerid/{id}` | Consulta comentarios de un usuario. |
| `GET /api/comments/byscore/asc` | Ordena los comentarios por puntuación ascendente. |
| `GET /api/comments/byscore/desc` | Ordena los comentarios por puntuación descendente. |

**Con quién trabaja:** `CommentService`. Para crear, modificar o eliminar, el controlador consulta los roles de `Authentication` y elige el método destinado al administrador o al cliente.

**Ejemplo:** Ana quiere dejar una opinión y una puntuación sobre el salón. El controlador recibe el comentario y el servicio revisa las condiciones aplicables. En la creación realizada por un cliente, el servicio comprueba la existencia de una reserva activa del espacio en estado confirmado o completado.

**Distinción útil:** un comentario es una opinión sobre un espacio; una notificación es un aviso del sistema sobre algo que ocurrió. Tienen propósitos y controladores diferentes.

## 13. Cómo conectar los archivos al estudiar una operación

Podés seguir este recorrido para entender el proyecto en un orden cercano a su uso:

1. **Registrarse e identificarse:** `AuthController`.
2. **Completar o actualizar los datos personales:** `ConsumerController`.
3. **Publicar y buscar un lugar:** `SpaceController`.
4. **Asociar sus imágenes:** `SpaceImageController`.
5. **Ofrecer prestaciones adicionales:** `SpaceServiceItemController`.
6. **Solicitar el lugar para un período:** `ReservationController`.
7. **Consultar o administrar los adicionales contratados:** `ServiceSelectedController`.
8. **Iniciar el pago y recibir su actualización:** `ReservationController` y `PaymentWebhookController`.
9. **Consultar los avisos generados:** `NotificationController`.
10. **Consultar o dejar una opinión:** `CommentController`.

Para estudiar un método concreto, leé primero su ruta y verbo HTTP, después sus parámetros, luego las comprobaciones de acceso y finalmente la llamada al servicio. Seguir ese servicio permite encontrar las reglas del negocio y el acceso a los datos. Así podés distinguir qué decide el controlador y qué trabajo delega.
