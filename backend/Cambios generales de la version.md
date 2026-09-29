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

## Edición de reservas

La edición conserva la entidad existente, su cliente, fecha de creación, estado y actividad.
Solo se permite mientras esté `TENTATIVE`, activa y sin pagos registrados. Revalida fechas,
disponibilidad (excluyendo la propia reserva), espacio activo, autorreserva, límite de cinco
reservas confirmadas/completadas y servicios del catálogo. Recalcula el precio en el servidor.
Los importes se redondean a dos decimales. Los campos `status`, `isActive`, `createdAt` y
`finalPrice` del DTO no cambian esos valores del servidor; un `idConsumer` distinto se rechaza.


La edición bloquea la reserva y los espacios involucrados para proteger la validación de
disponibilidad frente a solicitudes simultáneas. Las pruebas de edición usan H2 e incluyen
el intento concurrente de mover dos reservas al mismo espacio y horario.

Los cambios de pagos del punto 7 de la auditoría fueron retirados y quedan pendientes.
Esta corrección de edición no requiere una migración del esquema de pagos.

## Servicios contratados y edición de espacios 

`POST /api/servicesselected/insert/list/{idReservation}` recibe ahora IDs del catálogo:

```json
[{"idService": 1}, {"idService": 2}]
```

`idService` es el ID de `SpaceServiceItem`, no el ID de una selección anterior. Cada servicio
debe existir, estar activo y pertenecer al espacio reservado. Se rechazan listas vacías e IDs
inválidos o repetidos en la misma solicitud. Precio y descripción se copian del catálogo;
los campos adicionales enviados por el cliente, como `priceAtReservation`, `descriptionFrozen`
o `idReservation`, no intervienen. La reserva destino es la indicada en la URL. El payload
anterior sin `idService` devuelve 400 y los clientes deben adaptar esa llamada. La respuesta
GET conserva `ServiceSelectedDTO`, incluidos el precio y la descripción contratados.

Agregar, quitar uno o quitar todos los servicios actualiza el total en la misma transacción,
con bloqueo de la reserva. Se mantiene la restricción de edición: activa, `TENTATIVE` y sin
pagos registrados. La porción del alquiler ya cotizada se obtiene restando los adicionales
anteriores al total almacenado; luego se suman los precios congelados de los servicios que
quedan. Cambiar la tarifa del espacio o el catálogo no modifica retrospectivamente lo contratado.
Esto conserva cotizaciones coherentes existentes; no reconstruye ni corrige automáticamente
importes históricos que ya fueran inconsistentes.

`PUT /api/spaces/{id}` y `PUT /api/spaces/ownedspace/{id}` actualizan la entidad existente bajo
bloqueo. Permiten editar nombre, descripción, precio base, separación entre alquileres,
ubicación y política de cancelación. La ubicación y política deben existir, como antes.
Conservan propietario, actividad, fecha de publicación y la colección de servicios; imágenes
y reservas siguen asociadas. `idSpace` e `idConsumerOwner` pueden omitirse en edición; si se
incluyen, deben coincidir con los almacenados. Ninguna de estas rutas transfiere la propiedad.
`active`, `publicationDate` y `services` no reemplazan los valores almacenados durante la edición.
Para modificar el catálogo se usan las rutas de `/api/services`; consultar sus rutas exactas en
la documentación de la API. No se introduce un cambio de esquema ni una migración de base de datos.

## Privacidad del catálogo y registro 

Las lecturas de espacios devuelven `SpaceResponseDTO`, y las de imágenes devuelven
`SpaceImageResponseDTO`, con su espacio anidado convertido al mismo DTO. Sus propiedades
contienen valores y otros DTO, nunca entidades JPA. Se conserva la estructura de lectura
(`consumerOwner`, `location`, `cancellationPolicies`, `services`), pero el propietario público
incluye **únicamente `idConsumer`, `firstname` y `lastname`**. No incluye email, teléfono,
username, credenciales, roles ni estado de cuenta. Esta reducción también se aplica a las
listas de espacios del propietario y de administración. Los perfiles autorizados conservan
su información de contacto. Cuando un espacio aparece dentro de una reserva, la relación
`consumerOwner` también excluye contacto y credenciales.

Los dos endpoints de registro siguen disponibles y responden 201:

- `POST /api/auth/register`: recibe `RegistroDTO` con los datos del perfil.
- `POST /api/usuarios`: recibe `CredentialRegistrationDTO` con `username` y `password`,
  y crea el perfil con sus campos personales vacíos, como antes.

Ambos delegan en `RegistrationService.register`. La contraseña debe enviarse sin codificar;
`CredentialService.createCredential` aplica BCrypt **una única vez**. El servidor fija la
cuenta activa y el rol `ROLE_CLIENT`, sin tomar esos valores del cuerpo. Credencial y perfil
se insertan juntos dentro de una transacción: un fallo revierte ambos registros. Un username
repetido devuelve 400; la restricción única protege también los registros simultáneos y
ninguno de ellos puede actualizar una cuenta anterior. El registro no emite un token;
para obtenerlo se usa `/api/auth/login`.

No se requiere migración de esquema. Esta corrección arregla las altas nuevas: los hashes
que ya se guardaron con doble codificación no pueden recuperarse y esas cuentas necesitan
restablecer su contraseña. No se modifican automáticamente las credenciales existentes.
