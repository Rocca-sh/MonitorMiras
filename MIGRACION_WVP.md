# Documentación de Arquitectura: Migración a Motor SIP Nativo (GB28181)

## Contexto y Motivación
Anteriormente, el sistema dependía de **WVP-Pro** como un intermediario (middleware) para manejar la comunicación SIP con cámaras y DVRs (Hikvision, Dahua). Esto generaba:
1. Problemas de seguridad (mezclaba canales y dispositivos de distintos tenants/empresas).
2. Complejidad de infraestructura (un contenedor de Spring Boot extra, base de datos SQLite extra).
3. Dependencia en código mantenido por terceros.

**La solución:** Reemplazar WVP-Pro escribiendo nuestro propio motor SIP directamente en nuestro servidor Java (Spring Boot) utilizando la librería oficial **JAIN SIP**, y manteniendo **ZLMediaKit** exclusivamente como transcodificador RTP/WebRTC.

---

## 1. El Nuevo Flujo de Arquitectura

El flujo de video en vivo ahora funciona con la siguiente cadena de eventos:

### Fase 1: El Registro (Handshake)
1. El DVR es configurado para enviar telemetría a la IP de nuestro Servidor Java en el puerto `5060` (UDP).
2. El DVR dispara un evento `REGISTER`.
3. `ZlmController` escucha el evento mediante JAIN SIP, responde con un `200 OK` para confirmar la conexión.
4. Se extrae la **IP** y el **Puerto** físico del DVR desde la cabecera `Contact` y se guarda en **Redis** usando `RedisDvrService`.
5. En Redis se crea la llave `dvr:[SIP_ID]` con un **TTL (Tiempo de Vida) de 3 minutos**. 

### Fase 2: Latido de Vida (Keepalive)
1. Para cumplir con el protocolo GB28181, el DVR manda un mensaje `MESSAGE` (XML) con `<CmdType>Keepalive</CmdType>` cada 1 minuto.
2. `ZlmController` atrapa este mensaje, contesta `200 OK` al DVR, y busca al DVR en Redis.
3. Se actualiza el TTL de Redis a 3 minutos nuevamente.
> *Ventaja: Si alguien desconecta físicamente el DVR, los latidos dejarán de llegar. El TTL de Redis llegará a 0 y la llave se borrará automáticamente. El estado "Offline" se maneja sin necesidad de consultas a la base de datos PostgreSQL.*

### Fase 3: Petición de Video (INVITE y SDP)
1. El usuario final, a través de la web, manda a llamar el endpoint `/api/dvr/play/{sipId}` de `DvrController` y envía su Token JWT.
2. `DvrServImp` extrae el `orgId` (ID de la Empresa) contenido en el JWT.
3. Se verifica criptográficamente usando `SipCreator` que los primeros 10 dígitos del `sipId` solicitado coincidan con el `orgId`. **(Aislamiento de Tenants)**.
4. Si la seguridad pasa, `DvrServImp` le pregunta a Redis la IP y el Puerto del DVR.
5. Se invoca a `ZlmVideoRepo`, el cual:
   - Se comunica con la API de **ZLMediaKit** solicitando que se abra un puerto aleatorio de recepción de video (RTP).
   - Arma el texto **SDP** (Session Description Protocol) diciéndole al DVR a qué puerto exacto mandar su video.
   - Envía el comando `INVITE` utilizando JAIN SIP directamente al IP y puerto del DVR físico.
6. Finalmente, `ZlmVideoRepo` responde al Controller con los enlaces (Links) en múltiples protocolos (`ws-flv`, `webrtc`, `hls`, `rtsp`) para que el Frontend los reproduzca inmediatamente.

---

## 2. Reducción de la Infraestructura Docker

Con la eliminación de WVP-Pro, nuestro `docker-compose.yaml` se simplificó drásticamente a solo 3 servicios esenciales:

1. **ZLMediaKit** (`network_mode: host`): Se encarga de recibir el video crudo RTP que manda el DVR y transformarlo a formatos web-friendly (WebRTC, FLV) sobre la marcha.
2. **PostgreSQL**: Base de datos principal de la aplicación.
3. **Redis**: Administra eficientemente la caché, las sesiones y el estado de conexión "Online/Offline" de los DVRs basado en eventos en tiempo real.

---

## 3. Resumen de Clases Modificadas y Creadas

* `SipConfig.java`: Inicia la fábrica de JAIN SIP (Reference Implementation), define el `SipProvider` y abre los puertos de escucha 5060 TCP/UDP.
* `ZlmController.java`: Implementa la interfaz `SipListener`. Intercepta los eventos de red `REGISTER` y `MESSAGE` de las cámaras físicas.
* `RedisDvrService.java`: Fue simplificado para usar una lógica basada exclusivamente en Time-To-Live (TTL).
* `ZlmVideoRepo.java`: Es el núcleo de la solicitud de video. Actúa como cliente REST para comunicarse con ZLMediaKit y como cliente SIP para construir y enviar el objeto `INVITE`.
* `DvrServImp.java`: Controla la lógica de negocios y la capa de seguridad Multi-Tenant (validacion de JWT vs SIP_ID).
* `SipCreator.java`: Modificado para contener lógica unificada de parseo de identificadores SIP `getOrgIdFromSip(sipId)`.

---

### Mantenimiento Futuro
* Para gestionar el cierre del video, se puede atrapar un evento de cerrado desde el Frontend, que invocará al Servidor para mandar un mensaje SIP `BYE` a la cámara y cerrar el puerto en ZLMediaKit.
* El manejo del catálogo y subcanales del DVR se puede hacer extrayendo el listado en `ZlmController` cuando el DVR manda su XML con `<CmdType>Catalog</CmdType>`.
