package miras.monitor.Zlmedia.Repo;

import miras.monitor.Exceptions.Conflict.ConflictException;
import miras.monitor.Exceptions.Conflict.DvrRejectedException;
import miras.monitor.Exceptions.Timeout.DvrTimeoutException;
import miras.monitor.Utils.RedisDvrService;
import miras.monitor.Zlmedia.Config.SipConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;

import javax.sip.ClientTransaction;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import jakarta.annotation.PostConstruct;
import miras.monitor.Zlmedia.Controller.ZlmController;

@Repository
public class ZlmVideoRepo {

    @Autowired
    private SipConfig sipConfig;

    @Autowired
    private RedisDvrService redisDvrService;

    @Value("${zlm.api.url:http://127.0.0.1:80}")
    private String zlmApiUrl;

    @Value("${zlm.api.secret:change-me-zlm-secret}")
    private String zlmSecret;

    @Value("${zlm.public.ip:127.0.0.1}")
    private String zlmPublicIp;

    @Value("${sip.local.ip:127.0.0.1}")
    private String sipLocalIp;

    private final String SERVER_SIP_ID = "34020000002000000001";

    private final RestTemplate restTemplate = new RestTemplate();

    private int openRtpServer(String streamId) {
        try {
            String url = String.format("%s/index/api/openRtpServer?secret=%s&port=0&enable_tcp=1&stream_id=%s",
                    zlmApiUrl, zlmSecret, streamId);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                int code = (Integer) body.get("code");
                
                if (code == 0) {
                    int assignedPort = (Integer) body.get("port");
                    System.out.println("ZLM abrió puerto RTP: " + assignedPort + " para " + streamId);
                    return assignedPort;
                } else if (code == -300) {
                    System.out.println("El stream " + streamId + " ya existía en ZLM. Cerrando y reintentando...");
                    String closeUrl = String.format("%s/index/api/closeRtpServer?secret=%s&stream_id=%s", zlmApiUrl, zlmSecret, streamId);
                    restTemplate.getForEntity(closeUrl, Map.class);
                    
                    // Reintento recursivo (1 sola vez)
                    ResponseEntity<Map> retryResponse = restTemplate.getForEntity(url, Map.class);
                    if (retryResponse.getStatusCode().is2xxSuccessful() && retryResponse.getBody() != null) {
                        Map<String, Object> retryBody = retryResponse.getBody();
                        if (retryBody.containsKey("code") && ((Integer) retryBody.get("code") == 0)) {
                            return (Integer) retryBody.get("port");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    @PostConstruct
    public void syncWithZlmOnStartup() {
        try {
            System.out.println("Sincronizando estado con ZLMediaKit en el arranque...");
            String url = String.format("%s/index/api/getMediaList?secret=%s", zlmApiUrl, zlmSecret);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (body.containsKey("code") && (Integer) body.get("code") == 0) {
                    Object data = body.get("data");
                    if (data instanceof List) {
                        List<Map<String, Object>> streams = (List<Map<String, Object>>) data;
                        for (Map<String, Object> streamObj : streams) {
                            String app = (String) streamObj.get("app");
                            if ("rtp".equals(app)) {
                                String streamId = (String) streamObj.get("stream");
                                ZlmController.recoveredStreams.add(streamId);
                                System.out.println("Stream recuperado de ZLM en el arranque: " + streamId);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("No se pudo sincronizar con ZLM en el arranque: " + e.getMessage());
        }
    }

    public Map<String, String> getPlaybackLinks(String channelSipId, String dvrIp, int dvrPort, int quality) {
        if (ZlmController.activeDialogs.containsKey(channelSipId) || ZlmController.recoveredStreams.contains(channelSipId)) {
            return generatePlaybackLinks(channelSipId);
        }

        try {
            int zlmPort = openRtpServer(channelSipId);
            if (zlmPort == -1) {
                throw new miras.monitor.Exceptions.Conflict.ConflictException("No se pudo abrir puerto en ZLMediaKit");
            }

            String sdpData = generateSdpData(zlmPort);
            java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
            ZlmController.pendingFutures.put(channelSipId, future);

            try {
                sendInviteToDvr(channelSipId, dvrIp, dvrPort, sdpData, quality);
                future.get(10, java.util.concurrent.TimeUnit.SECONDS);
                return generatePlaybackLinks(channelSipId);
            } catch (java.util.concurrent.TimeoutException | InterruptedException | java.util.concurrent.ExecutionException e) {
                if (e instanceof java.util.concurrent.ExecutionException && e.getCause() instanceof miras.monitor.Exceptions.DvrRejectedException) {
                    throw (miras.monitor.Exceptions.DvrRejectedException) e.getCause();
                }
                throw new miras.monitor.Exceptions.Timeout.DvrTimeoutException("El dispositivo o cámara (" + channelSipId + ") no contestó en 10 segundos.");
            } finally {
                ZlmController.pendingFutures.remove(channelSipId);
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Error inesperado al pedir el stream", e);
        }
    }

    private String generateSdpData(int zlmPort) {
        String ssrc = "010000" + String.format("%04d", (int)(Math.random() * 10000));
        return "v=0\r\n" +
               "o=" + SERVER_SIP_ID + " 0 0 IN IP4 " + sipLocalIp + "\r\n" +
               "s=Play\r\n" +
               "c=IN IP4 " + sipLocalIp + "\r\n" +
               "t=0 0\r\n" +
               "m=video " + zlmPort + " RTP/AVP 96\r\n" +
               "a=recvonly\r\n" +
               "a=rtpmap:96 PS/90000\r\n" +
               "y=" + ssrc + "\r\n";
    }

    public String getCatalogWithTimeout(String dvrSipId, String dvrIp, int dvrPort) {
        try {
            SipProvider sipProvider = sipConfig.getSipProviderUdp();
            SipFactory sipFactory = SipFactory.getInstance();
            AddressFactory addressFactory = sipFactory.createAddressFactory();
            HeaderFactory headerFactory = sipFactory.createHeaderFactory();
            MessageFactory messageFactory = sipFactory.createMessageFactory();

            String xmlContent = "<?xml version=\"1.0\" encoding=\"GB2312\"?>\r\n" +
                                "<Query>\r\n" +
                                "<CmdType>Catalog</CmdType>\r\n" +
                                "<SN>" + (int)(Math.random() * 1000) + "</SN>\r\n" +
                                "<DeviceID>" + dvrSipId + "</DeviceID>\r\n" +
                                "</Query>\r\n";
            
            SipURI toUri = addressFactory.createSipURI(dvrSipId, dvrIp);
            toUri.setPort(dvrPort);
            javax.sip.address.Address toAddress = addressFactory.createAddress(toUri);
            javax.sip.header.ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

            SipURI fromUri = addressFactory.createSipURI(SERVER_SIP_ID, sipLocalIp);
            fromUri.setPort(5060);
            javax.sip.address.Address fromAddress = addressFactory.createAddress(fromUri);
            javax.sip.header.FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, "catalog" + System.currentTimeMillis());

            javax.sip.header.CallIdHeader callIdHeader = sipProvider.getNewCallId();
            javax.sip.header.CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, javax.sip.message.Request.MESSAGE);

            javax.sip.header.MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

            javax.sip.message.Request request = messageFactory.createRequest(
                    toUri, javax.sip.message.Request.MESSAGE, callIdHeader, cSeqHeader, fromHeader,
                    toHeader, java.util.Collections.singletonList(headerFactory.createViaHeader(sipLocalIp, 5060, "udp", null)),
                    maxForwards);

            javax.sip.header.ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("Application", "MANSCDP+xml");
            request.setContent(xmlContent, contentTypeHeader);

            sipProvider.sendRequest(request);
            System.out.println("===== PETICIÓN DE CATÁLOGO ENVIADA AL DVR " + dvrSipId + " =====");

            // Esperamos hasta 5 segundos a que el DVR responda el catálogo y ZlmController lo guarde
            int waitTime = 0;
            while (waitTime < 5000) {
                String channels = redisDvrService.getChannels(dvrSipId);
                if (channels != null) {
                    try { Thread.sleep(1000); } catch (Exception e) {} // Dar tiempo a que lleguen y se procesen los demás paquetes UDP
                    return redisDvrService.getChannels(dvrSipId);
                }
                try { Thread.sleep(200); } catch (Exception e) {}
                waitTime += 200;
            }

            redisDvrService.setDvrOffline(dvrSipId);
            throw new DvrTimeoutException("El dispositivo (" + dvrSipId + ") no respondió al catálogo. Puede estar apagado o fuera de línea.");
        } catch (DvrTimeoutException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error al solicitar el catálogo al DVR", e);
        }
    }

    private void sendInviteToDvr(String channelSipId, String shortSipId, String dvrIp, int dvrPort, String sdpData, int quality) {
        try {
            SipProvider sipProvider = sipConfig.getSipProviderUdp();
            SipFactory sipFactory = SipFactory.getInstance();
            AddressFactory addressFactory = sipFactory.createAddressFactory();
            HeaderFactory headerFactory = sipFactory.createHeaderFactory();
            MessageFactory messageFactory = sipFactory.createMessageFactory();

            // 1. To Header (Hacia el Canal del DVR)
            SipURI toUri = addressFactory.createSipURI(channelSipId, dvrIp);
            toUri.setPort(dvrPort);
            Address toAddress = addressFactory.createAddress(toUri);
            ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

            // 2. From Header (Desde nuestro Servidor)
            SipURI fromUri = addressFactory.createSipURI(SERVER_SIP_ID, sipLocalIp);
            fromUri.setPort(5060);
            Address fromAddress = addressFactory.createAddress(fromUri);
            FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, "tag" + System.currentTimeMillis());

            // 3. Request URI (La URL a contactar)
            SipURI requestUri = addressFactory.createSipURI(channelSipId, dvrIp);
            requestUri.setPort(dvrPort);

            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);
            MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

            ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
            ViaHeader viaHeader = headerFactory.createViaHeader(sipLocalIp, 5060, "udp", "z9hG4bK" + UUID.randomUUID().toString().replace("-", ""));
            viaHeaders.add(viaHeader);

            Request request = messageFactory.createRequest(requestUri, Request.INVITE, callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);

            // 9. Contact Header (Dónde pueden contestarnos)
            SipURI contactUri = addressFactory.createSipURI(SERVER_SIP_ID, sipLocalIp);
            contactUri.setPort(5060);
            Address contactAddress = addressFactory.createAddress(contactUri);
            ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
            request.addHeader(contactHeader);

            SubjectHeader subjectHeader = headerFactory.createSubjectHeader(channelSipId + ":" + quality + "," + SERVER_SIP_ID + ":0");
            request.addHeader(subjectHeader);

            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");

            request.setContent(sdpData, contentTypeHeader);

            ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(request);
            clientTransaction.sendRequest();

            System.out.println("===== INVITE ENVIADO AL DVR =====");
            System.out.println("Destino: " + dvrIp + ":" + dvrPort + " (Canal: " + channelSipId + ")");
            
        } catch (javax.sip.TransactionUnavailableException e) {
            throw new miras.monitor.Exceptions.NotFound.NotFoundException("Canal no encontrado o el DVR rechazó la conexión (Offline o SIP ID incorrecto).");
        } catch (Exception e) {
            System.err.println("Error al construir/enviar el INVITE:");
            e.printStackTrace();
            throw new RuntimeException("Error al enviar INVITE al DVR");
        }
    }

    private Map<String, String> generatePlaybackLinks(String streamId) {
        Map<String, String> links = new java.util.HashMap<>();
        String app = "rtp";

        links.put("ws_flv", "ws://" + zlmPublicIp + "/" + app + "/" + streamId + ".live.flv");
        links.put("http_flv", "http://" + zlmPublicIp + "/" + app + "/" + streamId + ".live.flv");
        links.put("hls", "http://" + zlmPublicIp + "/" + app + "/" + streamId + "/hls.m3u8");
        links.put("webrtc", "ws://" + zlmPublicIp + "/index/api/webrtc?app=" + app + "&stream=" + streamId + "&type=play");
        links.put("rtsp", "rtsp://" + zlmPublicIp + "/" + app + "/" + streamId);

        return links;
    }

    public void stopStream(String streamId) {
        System.out.println("Solicitud de stopStream ignorada para " + streamId + ". ZLM se encargará de cerrar el stream automáticamente cuando no haya espectadores (webhook on_stream_none_reader).");
    }
}
