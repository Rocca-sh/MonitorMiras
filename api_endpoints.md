# Documentación Completa de Endpoints (Backend API)

Este documento detalla todos los endpoints disponibles en el backend, incluyendo inicio de sesión, creación de cuentas, organizaciones y gestión de dispositivos (DVRs).

---

## 1. Módulo de Autenticación y Usuarios (`/auth/users`)

Estos endpoints son **Públicos** y no requieren enviar el token JWT, con excepción de algunas acciones específicas. 

### 1.1. Crear Cuenta (Registro)
Crea un nuevo usuario en el sistema.

* **Ruta:** `POST /auth/users`
* **Cuerpo (Request):**
```json
{
  "name": "Juan Perez",
  "email": "juan@example.com",
  "passwd": "passwordDeUsuario",
  "ulidOrg": "ULID_DE_LA_ORGANIZACION", // Opcional, solo si acabas de crear la org y quieres ser OWNER
  "orgPassword": "passwordDeLaEmpresa" // Opcional, requerido solo si envías ulidOrg
}
```
* **Respuesta (201 Created):** Retorna directamente el Token JWT en formato de texto. Si pasaste `ulidOrg` y `orgPassword` correctos, el token ya viene con el rol `OWNER` de esa empresa.
* **Flujo Frontend:** Guarda este Token en `localStorage` o cookies para usarlo en el resto de peticiones.

### 1.2. Iniciar Sesión (Contraseña)
* **Ruta:** `POST /auth/users/login/password`
* **Cuerpo (Request):**
```json
{
  "email": "juan@example.com",
  "psswd": "123456"
}
```
* **Respuesta (200 OK):** Retorna el Token JWT.

### 1.3. Iniciar Sesión (Código al Email) - Generar Código
* **Ruta:** `POST /auth/users/login/code/generate`
* **Cuerpo (Request):**
```json
{
  "email": "juan@example.com"
}
```
* **Respuesta (200 OK):** Vacía. Un correo se ha enviado al usuario con un código (OTP).

### 1.4. Iniciar Sesión (Código al Email) - Verificar Código
* **Ruta:** `POST /auth/users/login/code/verify`
* **Cuerpo (Request):**
```json
{
  "key": "juan@example.com",
  "psswd": "123456" // En este caso el DTO pide la propiedad "psswd" pero se manda el código numérico recibido.
}
```
* **Respuesta (200 OK):** Retorna el Token JWT.

### 1.5. Vincular Usuario a una Organización (Empresa)
Vincula a un usuario con una empresa existente. 

* **Ruta:** `POST /auth/users/link-org`
* **Cuerpo (Request):**
```json
{
  "email": "juan@example.com",
  "ulidOrg": "ULID_DE_LA_ORGANIZACION",
  "role": "MEMBER", // o "VIEWER"
  "orgPassword": "passwordDeLaEmpresa",
  "code": "123456" // Requerido solo si el rol es MEMBER. Si el rol es VIEWER, se puede omitir o mandar vacío.
}
```
> [!NOTE]  
> **Sobre el Rol VIEWER:** Si deseas vincular a un usuario con rol `VIEWER` (solo lectura, menos permisos), **no se requiere el código OTP (`code`)**, basta con la contraseña de la empresa (`orgPassword`).

* **Respuesta (200 OK):** Retorna un NUEVO Token JWT. Este token ya contiene el permiso y rol para acceder a la organización.

### 1.6. Cerrar Sesión
* **Ruta:** `POST /auth/users/logout/{ulid}`
* **Parámetros en ruta:** `ulid` del usuario.
* **Respuesta (200 OK):** Vacía.

---

## 2. Módulo de Organizaciones (Empresas) (`/org`)

Módulo para administrar las empresas a las que pertenecen los DVRs y los Usuarios. (Requiere JWT).

### 2.1. Crear una Organización
* **Ruta:** `POST /org/create`
* **Cuerpo (Request):**
```json
{
  "name": "Mi Empresa de Seguridad",
  "email": "contacto@miempresa.com",
  "passwd": "passwordSeguro123"
}
```
* **Respuesta (201 Created):** Retorna el `ULID` de la nueva organización.

### 2.2. Generar Código de Invitación para la Organización
* **Ruta:** `POST /org/code/generate`
* **Cuerpo (Request):**
```json
{
  "ulidOrg": "ULID_DE_LA_ORGANIZACION"
}
```
* **Respuesta (200 OK):** Vacía. Envía un código al correo principal de la organización para que puedan invitar a nuevos miembros.

---

## 3. Módulo de Dispositivos DVRs (`/api/dvr`)

Todos los endpoints bajo `/api/dvr/` requieren autenticación y que el usuario esté vinculado a una organización (`orgId` inyectado vía JWT).

### Flujo de Vistas en el Frontend
1. **Listar DVRs (`GET /api/dvr/list`)** para renderizar en pantalla.
2. Al hacer clic en un DVR, llama a **Consultar Canales (`GET /api/dvr/channels/{sipId}`)**.
3. Al reproducir un canal, llama a **Reproducir Canal (`GET /api/dvr/play/{sipId}?channelId={channelId}`)** para obtener enlaces y dárselos al reproductor.

### 3.1. Listar Dispositivos
* **Ruta:** `GET /api/dvr/list`
* **Respuesta (200 OK):** Arreglo JSON de objetos `Dvr`.

### 3.2. Registrar un nuevo DVR
* **Ruta:** `POST /api/dvr/register`
* **Cuerpo (Request):** 
```json
{
  "name": "Cámara Exterior",
  "sipId": "34020000001110000002",
  "protocol": "GB28181"
}
```
* **Respuesta (201 Created):** Retorna el objeto `Dvr` creado en la base de datos con su `ulid`.

### 3.3. Actualizar Ajustes del DVR
* **Ruta:** `PUT /api/dvr/settings/{ulid}`
* **Cuerpo (Request):** 
```json
{
  "name": "Nuevo Nombre",
  "sipId": "NUEVOSIP",
  "protocol": "GB28181"
}
```
* **Respuesta (200 OK):** Modelo actualizado.

### 3.4. Eliminar un DVR
* **Ruta:** `DELETE /api/dvr/remove/{ulid}`
* **Respuesta (200 OK):** Vacía (Borrado permanente).

### 3.5. Obtener Canales del DVR
* **Ruta:** `GET /api/dvr/channels/{sipId}`
* **Respuesta (200 OK):** Devuelve el JSON con la lista de canales proveniente del servidor de streaming.

### 3.6. Obtener Enlaces de Reproducción (Streaming)
* **Ruta:** `GET /api/dvr/play/{sipId}`
* **Query Params (Opcional):** `?channelId={id_del_canal}` 
* **Respuesta (200 OK):** Devuelve enlaces de video HTTP-FLV, HLS, WebRTC, etc.

---

## 4. Eventos en Tiempo Real (Server-Sent Events / SSE)

Para que el frontend pueda pintar de verde (online) o gris (offline) las cámaras sin necesidad de hacer polling, el backend provee un endpoint de streaming de eventos que manda un arreglo actualizado cada vez que un DVR se conecta o desconecta.

### 4.1. Suscripción a Eventos de DVRs
Abre una conexión persistente para escuchar el estado de tus DVRs.

* **Ruta:** `GET /api/view/dvrs/stream` (o `GET /api/view/wvp/stream`)
* **Headers requeridos:** `Authorization: Bearer <token>`
* **Eventos recibidos (`dvr-update`):** El servidor mandará un evento llamado `dvr-update` con un arreglo (Set) que contiene **únicamente los `sipId` de los DVRs que están Online**. Si un DVR no viene en ese arreglo, significa que está Offline.

**Ejemplo de cómo consumirlo en Javascript:**
```javascript
const eventSource = new EventSource('/api/view/dvrs/stream?token=TU_JWT');
// (Si usas EventSource nativo no puedes mandar headers, 
// puedes mandar el JWT por parámetro o usar @microsoft/fetch-event-source)

eventSource.addEventListener('dvr-update', (event) => {
  const onlineDvrs = JSON.parse(event.data); 
  // onlineDvrs = ["34020000001110000001", "34020000001110000002"]
  console.log("Cámaras actualmente online:", onlineDvrs);
});
```

