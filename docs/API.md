# Contrato de la API

## Conexión y autenticación

URL base local: `http://localhost:8080/api`. El backend admite los orígenes configurados en `CORS_ALLOWED_ORIGINS`.

El catálogo público funciona sin token. Para operaciones autenticadas enviá el JWT en `Authorization`. El login devuelve **texto plano con el prefijo `Bearer `**, no un objeto JSON.

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"usuario","password":"contraseña"}'

curl http://localhost:8080/api/reservations/me \
  -H 'Authorization: Bearer <token>'
```

Registro recomendado: `POST /auth/register` con `firstname`, `lastname`, `email`, `phone`, `username`, `password`. Respuesta 201 en texto. Logout: `POST /auth/logout` con Authorization.

Roles existentes: `ROLE_CLIENT`, `ROLE_ADMIN`. Las decisiones de autorización se validan en el servidor. Consultá los huecos actuales de permisos en [ARCHITECTURE.md](ARCHITECTURE.md).

## Operaciones principales

Todas las rutas de la tabla son relativas a `/api`. Para el catálogo completo de métodos, payloads y esquemas consultá `/v3/api-docs` o `/swagger-ui/index.html` del servidor en ejecución. Algunas anotaciones de ejemplos antiguas están incompletas; los DTO y estos ejemplos describen los campos vigentes.

| Área | Método y ruta | Uso |
| --- | --- | --- |
| Espacios | `GET /spaces`, `GET /spaces/{id}` | Catálogo público y detalle |
| Búsqueda | `POST /spaces/byfields` | Filtros en JSON, acceso público |
| Espacios propios | `GET /spaces/ownedspaces`, `POST /spaces/ownedspaces/byfields` | Lista y filtros del usuario autenticado |
| Publicación | `POST /spaces/ownedspace` | Crea un espacio del usuario; responde 201 con `{ "idSpace": 1 }` |
| Edición propia | `PUT /spaces/ownedspace/{id}`, `DELETE /spaces/ownedspace/{id}` | Edición y baja lógica; revisar pendientes de edición |
| Administración | `GET /spaces/showinactives`, `POST /spaces/byfields/showinactives`, `POST /spaces`, `PUT /spaces/{id}`, `DELETE /spaces/{id}` | Requiere administrador |
| Imágenes | `GET /spaceimages/byspaceid/{id}` | Imágenes públicas del espacio |
| Imágenes | `POST /spaceimages`, `PUT /spaceimages/{id}`, `DELETE /spaceimages/{id}` | Gestión de imágenes con autenticación |
| Servicios | `GET /services/space/{idSpace}` | Opcionales del espacio; autenticado |
| Servicios | `POST /services/insert`, `PUT /services/update/{id}`, `DELETE /services/space/{idSpace}/delete/{id}` | Gestión de servicios |
| Reservas | `POST /reservations`, `GET /reservations/me`, `GET /reservations/{id}` | Crear y consultar reservas |
| Reservas generales | `GET /reservations` | Lista global actual; pendiente limitar visibilidad por rol/propietario |
| Estados | `PUT /reservations/confirm/{id}`, `/reject/{id}`, `/cancel/{id}`, `/complete/{id}` | Cambios de estado; permisos actuales requieren revisión |
| Pago | `POST /reservations/{id}/checkout` | Devuelve `{ "initPoint": "..." }`; reserva confirmada y cliente titular |
| Servicios reservados | `GET /servicesselected/reservation/{idReservation}`, `POST /servicesselected/insert/list/{idReservation}`, `DELETE /servicesselected/delete/{id}` | Opcionales congelados en la reserva |
| Comentarios | `GET /comments/byspaceid/{id}`, `POST /comments`, `PUT /comments/{id}`, `DELETE /comments/{id}` | Consulta pública y gestión autenticada |
| Notificaciones | `GET /notifications/me`, `GET /notifications/unread-count`, `POST /notifications/{id}` | Lista, contador y marcar vista |
| Perfil | `GET /usuarios/{id}`, `PUT /usuario`, `DELETE /usuario` | Consulta, actualización y baja |
| Usuarios | `GET /usuarios`, `POST /usuarios/byfields`, `DELETE /usuarios/{id}` | Gestión administrativa |

## Filtrar espacios

```json
{
  "nameSpace": "salón",
  "minPrice": 1000,
  "maxPrice": 20000,
  "idConsumerOwner": null,
  "idLocation": null,
  "lat": -34.6037,
  "lng": -58.3816,
  "radious": 5
}
```

`radious` conserva la escritura existente y se expresa en kilómetros. Los campos no usados pueden ser `null`. El propietario de los filtros propios se toma de la sesión. Los filtros administrativos y propios aceptan POST; el GET anterior se mantiene por compatibilidad.

## Publicar un espacio propio

`POST /spaces/ownedspace`:

```json
{
  "nameSpace": "Sala de reuniones",
  "description": "Sala para reuniones de equipo",
  "location": { "latitude": -34.6037, "longitude": -58.3816 },
  "cancellationPolicies": "FLEXIBLE",
  "basePrice": 1000,
  "bufferTime": 30,
  "services": [ { "description": "Proyector", "price": 250 } ]
}
```

`basePrice` es precio por hora. `bufferTime` son minutos enteros positivos: la disponibilidad usa `plusMinutes`. Las políticas admitidas son `FLEXIBLE`, `MODERATED`, `STRICT` y deben existir en la base antes de publicar. La aplicación no carga automáticamente sus condiciones comerciales.

## Crear una reserva

`POST /reservations`, con Authorization y fechas futuras:

```json
{
  "title": "Reunión de equipo",
  "description": "Planificación mensual",
  "fromDate": "2027-06-15T10:00:00",
  "untilDate": "2027-06-15T12:00:00",
  "idSpace": 1,
  "idServicesSelec": [2]
}
```

Las fechas son horas locales sin offset, con segundos (`yyyy-MM-dd'T'HH:mm:ss`). La aplicación conserva el tratamiento actual de zonas horarias; no introducir conversiones a UTC sin revisar ese contrato.

El backend obtiene el cliente de la sesión, fija `TENTATIVE`, calcula precio por duración más opcionales, guarda la reserva y notifica al dueño. No enviar precios finales, estado o identidad como decisiones del cliente. El precio definitivo es el calculado por el servidor.

Estados existentes: `TENTATIVE`, `CONFIRMED`, `REJECTED`, `CANCELLED`, `COMPLETED`.

El pago se inicia con `POST /reservations/{id}/checkout` y se abre la URL `initPoint`. Los retornos se configuran con `PAYMENT_SUCCESS_URL`, `PAYMENT_FAILURE_URL`, `PAYMENT_PENDING_URL`; llegar a una página de éxito no prueba por sí mismo que se haya acreditado un pago.

## Formatos de respuesta

- Las listas de espacios y reservas devuelven entidades con objetos anidados (`consumerOwner`, `location`, `space`, `consumer`, `services`). No suponer que todas las respuestas usan solo IDs.
- Los hashes de contraseñas ya no se incluyen en respuestas.
- Éxitos y errores pueden ser JSON, texto plano o un cuerpo vacío. Revisar el estado HTTP y admitir esos formatos.
- Notificaciones incluyen `idNotification`/`id` e `isSeen`/`seen` como alias; `createdAt` es epoch UTC en milisegundos. Listarlas marca las devueltas como vistas.
- `SpaceImageDTO` recibe `idSpace`, `fileName`, `urlImage`, `dateSend`; no existe un endpoint multipart de archivos. Preferir URLs de imágenes ya alojadas.
