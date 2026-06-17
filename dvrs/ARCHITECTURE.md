# Arquitectura del Proyecto: Miras Monitor (Servidor DVR)

Este documento resume la arquitectura general, los patrones de diseño y las tecnologías empleadas en el desarrollo del backend del sistema de monitoreo de cámaras.

## 1. Resumen General del Proyecto
Miras Monitor es un backend desarrollado en **Java con Spring Boot 3** diseñado para administrar organizaciones, usuarios y cámaras de videovigilancia (DVRs). Actúa como el "Cerebro" de un sistema de streaming de video, delegando la carga pesada de transcodificación de video a un **Media Server externo (ej. ZLMediaKit)**.

### Tecnologías Principales:
* **Framework:** Spring Boot (Java 21)
* **Base de Datos:** PostgreSQL (Persistencia de Entidades)
* **Caché y Estado Real:** Redis (Almacenamiento de tokens temporales y estados ONLINE/OFFLINE de cámaras)
* **Autenticación:** JSON Web Tokens (JWT) Stateless
* **Identificadores:** ULID (Universally Unique Lexicographically Sortable Identifier)

---

## 2. Modelos Principales (Dominio)

El sistema está dividido en dominios claros (Domain-Driven Design básico):

* **Org (Organización/Empresa):** Entidad principal. Todo en el sistema (Usuarios y Cámaras) pertenece a una Organización.
* **User (Usuario):** Miembros de una organización. Se diferencian por **Roles** (`ADMIN`, `OWNER`, `MEMBER`, `VIEWER`).
* **Dvr (Dispositivo):** Representa una cámara o grabador. Está enlazada a una `Org` y a un `User` (creador). Contiene metadata para conectarse vía GB28181, RTSP, etc.

---

## 3. Arquitectura de Seguridad (JWT y Roles)

El sistema es **Completamente Stateless (Sin estado)**, eliminando las sesiones en memoria del servidor (`SessionCreationPolicy.STATELESS`).

1. **Login:** El usuario se autentica y el servidor firma un `JWT` que en su payload contiene: `userId`, `role`, y `orgId`.
2. **JwtFilter:** Intercepta todas las peticiones HTTP (excepto webhooks y endpoints públicos de auth). Valida la firma del token.
3. **UserPrincipal:** Si el token es válido, el filtro instancia la clase `UserPrincipal` inyectando los datos extraídos en el **SecurityContext** de Spring.
4. **Controladores Limpios:** Los controladores reciben directamente `@AuthenticationPrincipal UserPrincipal principal`. Ya no necesitan parsear headers ni hacer queries extraídos de bases de datos para saber la empresa del usuario.

### Jerarquía de Rutas (`SecurityConfig`):
* `/auth/**` y `/api/webhook/**` -> Públicas.
* `/api/dvr/view/**` -> Roles: `VIEWER`, `MEMBER`, `OWNER`, `ADMIN` (Solo Lectura).
* `/api/dvr/**` -> Roles: `MEMBER`, `OWNER`, `ADMIN` (Edición y Borrado).

---

## 4. Integración con Media Server (Webhooks y Redis)

El sistema evita la saturación mediante una arquitectura "Push" (Webhooks) en lugar de "Polling".

* **Autorización (`/on_publish`):** Cuando una cámara intenta mandar video al Media Server, este le dispara un Webhook a nuestro backend de Spring. Spring revisa la base de datos de PostgreSQL y acepta (HTTP 200 con `code: 0`) o rechaza la conexión.
* **Estado en Tiempo Real (`/on_stream_changed` y `Redis`):** Es ineficiente guardar si una cámara está Online/Offline en PostgreSQL. Por lo tanto, el Webhook de cambio de estado interactúa exclusivamente con **Redis** guardando la llave `dvr:status:{ulid}`.

---

## 5. Manejo Global de Excepciones

Para mantener las respuestas del API consistentes en todo el frontend, se emplea una clase con `@RestControllerAdvice` (Exception Handler Global).

### Excepciones Personalizadas:
* **`BadRequestException` (HTTP 400):** Se lanza cuando el usuario envía datos erróneos o inválidos.
* **`UnauthorizedException` (HTTP 401/403):** Se lanza por problemas de tokens, permisos insuficientes o violaciones de dominio cruzado (ej. Intentar editar un DVR de otra empresa).
* **`NotFoundException` (HTTP 404):** Entidades inexistentes en base de datos.
* **`InternalServerErrorException` (HTTP 500):** Fallos genéricos de servidor o base de datos.

El `GlobalExceptionHandler` captura cualquiera de estas excepciones arrojadas desde los *Servicios* y las envuelve en un JSON uniforme con `{ "error": "...", "status": 404, "timestamp": "..." }`, permitiendo que el Frontend las procese dinámicamente sin que la app crashee.

---

## 6. Optimizaciones Notables (JPA)

* **Referencias Proxy (`getReferenceById`):** Al guardar relaciones Foráneas (Ej. enlazar una `Org` a un `User`), se evita hacer sentencias `SELECT` innecesarias. Se inyectan entidades Proxy generadas al vuelo para optimizar las consultas a la base de datos y hacer directamente el `INSERT/UPDATE`.
* **Uso de DTOs Exclusivos:** Los Controladores reciben y devuelven `Data Transfer Objects` (DTOs), aislando al modelo de base de datos puro (Model) de inyecciones de campos malintencionados.
