# Documentación de Migración: WVP-Pro a Solución Nativa (JAIN-SIP + ZLMediaKit)

Este documento describe la arquitectura, los problemas resueltos y las configuraciones implementadas para sustituir el sistema obsoleto WVP-Pro por un desarrollo a la medida en Spring Boot utilizando **JAIN-SIP** y **ZLMediaKit**.

---

## 1. Arquitectura y Componentes
La nueva arquitectura se basa en 3 pilares, empaquetados de forma mínima y limpia usando `docker-compose.yml`:
1. **Spring Boot (JAIN-SIP)**: Actúa como el Servidor SIP (GB28181) que recibe los registros de los DVRs, maneja los latidos (Keepalives) y orquesta el envío de comandos (INVITE, ACK) para iniciar el video.
2. **ZLMediaKit**: Servidor de streaming multimedia en C++ ultrarrápido. Recibe los paquetes RTP por UDP desde los DVRs y los convierte al vuelo a RTSP, RTMP, HLS, y WebRTC para el consumo de los usuarios o reproductores como VLC.
3. **Redis & PostgreSQL**: Bases de datos para almacenar el estado en vivo de las cámaras y la persistencia de usuarios y catálogos.

---

## 2. Flujo de Trabajo (El "Handshake" GB28181)
Cuando el cliente pide reproducir un video (`GET /api/dvr/play/{dvrSipId}?channelId={channelSipId}`), ocurren los siguientes pasos automatizados en nuestro código:

1. **Apertura de Puerto**: Spring Boot llama a la API REST de ZLMediaKit (`/index/api/openRtpServer`) pidiendo que abra un puerto aleatorio (ej. 30015).
2. **Creación del SDP**: Spring Boot genera un manifiesto (SDP) indicando la IP pública/local del servidor (`sip.local.ip`) y el puerto asignado por ZLM.
3. **Petición (INVITE)**: Se envía un comando `INVITE` SIP hacia la cámara pidiendo que mande el stream de video a nuestra IP y puerto.
4. **Confirmación (200 OK + ACK)**: La cámara responde que acepta (`200 OK`). **Crítico**: Nuestro código debe responder un `ACK` para sellar el trato.
5. **Streaming**: La cámara comienza a mandar UDP hacia ZLMediaKit. ZLM detecta el flujo y Spring Boot le entrega al usuario los links de reproducción (RTSP/WebRTC).

---

## 3. Problemas Solucionados durante el Desarrollo

### A. Dependencia Faltante de JAIN-SIP
* **Error**: `NoClassDefFoundError: org/apache/log4j/Logger` al intentar inicializar el SIP Stack.
* **Solución**: Agregamos la dependencia clásica de `log4j:1.2.17` en el `pom.xml`, ya que la librería antigua JAIN-SIP depende fuertemente de ella en tiempo de ejecución.

### B. ZLMediaKit Pidiendo Contraseña en VLC (Webhooks Muertos)
* **Error**: Al abrir el link RTSP en VLC, pedía un usuario y contraseña infinitamente.
* **Causa**: ZLMediaKit tenía configurados los `Webhooks` hacia el servidor WVP-Pro antiguo (`http://127.0.0.1:18080/index/hook/...`). Al no existir ya WVP, ZLM bloqueaba todas las reproducciones.
* **Solución**: Editamos el `config.ini` de ZLMediaKit y cambiamos `[hook] enable=0`.

### C. Confusión con "UnknownHostException" (JAIN-SIP URI)
* **Error**: Al enviar el `INVITE`, JAIN-SIP intentaba resolver el SIP ID de la cámara como si fuera un dominio web (ej. buscando `44010200491310000030` en DNS).
* **Solución**: Separamos estrictamente la IP del puerto al usar `addressFactory.createSipURI(id, ip)` y llamamos a `toUri.setPort(...)` explícitamente.

### D. Protección contra Dobles Peticiones (Error -300 de ZLM)
* **Error**: Si el usuario recargaba la página rápido, ZLM respondía `-300 This stream already exists` y el sistema fallaba sin entregar el link.
* **Solución**: El método `openRtpServer` ahora atrapa el código `-300`, llama recursivamente a `/closeRtpServer` para limpiar puertos "zombies" y vuelve a abrir el puerto limpiamente.

### E. Cámaras Enviando Video al Vacío (El factor ACK y la IP Local)
* **Error**: ZLM abría el puerto pero recibía un "Timeout" a los 15 segundos porque el video nunca llegaba.
* **Soluciones**: 
    1. Agregamos el método `processResponse` en `ZlmController` para enviar obligatoriamente el mensaje `ACK` después de un `200 OK`. Sin esto, las cámaras se rehúsan a mandar el streaming.
    2. Modificamos el SDP para inyectar correctamente la `sip.local.ip`. Antes enviaba `127.0.0.1`, causando que la cámara se intentara enviar el video a sí misma en lugar de a nuestro servidor.

### F. Firewall Bloqueando Tráfico UDP
* **Error**: VLC daba Timeouts pese a que el "Handshake" era perfecto.
* **Solución**: Abrimos explícitamente el rango RTP (`30000-40500` UDP/TCP) y el puerto RTSP (`554` UDP/TCP) en `firewalld` (Fedora), permitiendo a ZLM recibir tráfico directo de las cámaras.

---

## 4. Notas para Despliegues a Producción
* Asegurarse de que en `application.properties` las variables `sip.local.ip` y `zlm.public.ip` tengan la IP pública o LAN real del servidor físico.
* En `docker-compose.yml`, ZLMediaKit **debe** permanecer en `network_mode: host` para poder tomar puertos UDP bajo demanda sin ser restringido por el proxy interno de Docker.
* Si se requiere seguridad en las rutas (contraseñas en los videos), los Webhooks en ZLMediaKit pueden reactivarse más adelante apuntando hacia un nuevo controlador REST hecho a la medida en nuestro Spring Boot en vez del viejo WVP.
