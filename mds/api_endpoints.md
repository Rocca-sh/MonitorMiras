# Documentación Completa de Endpoints (Backend API)

Este documento detalla todos los endpoints disponibles en el backend, actualizados con el nuevo flujo de "Plug & Play", visualización de DVRs, manejo de streams por lote y pruebas rápidas.

---

## 0. Pruebas Rápidas (Seed)

Endpoint diseñado exclusivamente para ambiente de desarrollo y testing.

### 0.1. Generar Empresa, Usuario y Token de Prueba
Crea (si no existe) la empresa y usuario de prueba, y retorna un JWT válido inmediatamente.
* **Ruta:** `GET /org/test/seed`
* **Respuesta Esperada (200 OK):**
```json
{
  "password": "root",
  "orgSipId": "4401020049",
  "message": "Empresa y Usuario de prueba listos",
  "email": "test@empresa.com",
  "token": "eyJhbGciOiJIUzI1Ni..."
}
```

---

## 1. Módulo de Dispositivos DVRs (`/api/dvr`)

Todos estos endpoints requieren autenticación pasando tu token en el Header `Authorization: Bearer <token>`.

### 1.1. Listar TODOS los Dispositivos (Asignados y Huérfanos)
Este es el endpoint principal para poblar la vista principal. Mezcla los equipos guardados en la BD con los que están conectados en vivo a través de Redis.
* **Ruta:** `GET /api/dvr/list`
* **Respuesta:** Arreglo con objetos de tus DVRs. Los que estén detectados pero no guardados vendrán con `"isAssigned": false` y `"name": "Sin asignar"`.

### 1.2. Registrar/Asignar un DVR (Plug & Play)
Toma el `sipId` de un dispositivo "Sin Asignar" del paso anterior y lo guarda en tu base de datos de Postgres.
* **Ruta:** `POST /api/dvr/register`
* **Cuerpo (Request):** 
```json
{
  "name": "Cámara Exterior",
  "sipId": "44010200491110000030",
  "protocol": "GB28181"
}
```
* **Respuesta (201 Created):** Retorna el objeto `Dvr` creado en la base de datos con su `ulid`.

### 1.3. Obtener Canales del DVR
* **Ruta:** `GET /api/dvr/channels/{sipId}`
* **Respuesta:** Devuelve la lista de canales (cámaras físicas) conectadas al DVR.

### 1.4. Eliminar un DVR
* **Ruta:** `DELETE /api/dvr/remove/{ulid}`
* **Respuesta:** Vacía (Borrado permanente de la base de datos).

---

## 2. Reproducción de Video (Streaming)

### 2.1. Solicitar Video en Lote (Batch Play)
Úsalo para encender una o múltiples cámaras de un jalón y obtener sus links de reproducción.
* **Ruta:** `POST /api/dvr/play/batch`
* **Cuerpo (Request):**
```json
{
  "dvrSipId": "44010200491110000030",
  "channelIds": [
    "44010200491320000001",
    "44010200491320000002"
  ]
}
```
* **Respuesta (200 OK):**
```json
{
  "44010200491320000001": {
    "ws_flv": "ws://192.168.1.8/rtp/44010200491320000001.live.flv",
    "http_flv": "http://...",
    "hls": "http://...",
    "webrtc": "ws://...",
    "rtsp": "rtsp://..."
  },
  "44010200491320000002": {
    "ws_flv": "ws://192.168.1.8/rtp/44010200491320000002.live.flv",
    ...
  }
}
```

### 2.2. Apagar un Canal Manualmente (Stop)
Llama a este endpoint cuando el usuario cierre el reproductor de video en el Frontend. Le avisa al DVR que deje de mandar datos por la red.
* **Ruta:** `POST /api/dvr/stop/{channelId}`
* **Reemplazo en URL:** Cambia `{channelId}` por el SIP del canal (ej. `44010200491320000001`).
* **Respuesta:** 200 OK sin cuerpo.

---

## 3. Eventos en Tiempo Real (SSE)

Mantén la lista de DVRs actualizada al instante en la UI sin hacer peticiones HTTP en bucle. El servidor notifica tan pronto como una cámara se enciende o se apaga.

### 3.1. Escuchar Streaming de Eventos
* **Ruta:** `GET /api/view/dvr-sse/stream`
* **Headers:** `Authorization: Bearer <token>`, `Accept: text/event-stream`

**Comportamiento:**
1. Al conectar, recibirás inmediatamente un evento `init`.
2. Milisegundos después, recibirás el evento `dvr-update` con el estado **actual** de los DVRs.
3. El puerto se quedará "colgado" escuchando actualizaciones futuras.

**Ejemplo de cómo consumirlo (con Fetch API / @microsoft/fetch-event-source):**
```javascript
import { fetchEventSource } from '@microsoft/fetch-event-source';

fetchEventSource('http://localhost:8080/api/view/dvr-sse/stream', {
  headers: {
    'Authorization': 'Bearer ' + tuTokenJWT
  },
  onmessage(ev) {
    if (ev.event === 'dvr-update') {
      const onlineDvrs = JSON.parse(ev.data); 
      // onlineDvrs = ["44010200491110000030", ...]
      console.log("Cámaras Online En Vivo:", onlineDvrs);
      // Aquí actualizas tu UI (Ej: encender foquitos verdes)
    }
  }
});
```
