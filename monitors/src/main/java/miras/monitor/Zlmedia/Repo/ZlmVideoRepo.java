package miras.monitor.Zlmedia.Repo;

import miras.monitor.Exceptions.Conflict.ConflictException;
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

@Repository
public class ZlmVideoRepo {

    @Autowired
    private SipConfig sipConfig;

    @Value("${zlm.api.url:http://127.0.0.1:80}")
    private String zlmApiUrl;

    @Value("${zlm.api.secret:change-me-zlm-secret}")
    private String zlmSecret;

    @Value("${zlm.public.ip:127.0.0.1}")
    private String zlmPublicIp;

    @Value("${sip.local.ip:127.0.0.1}")
    private String sipLocalIp;

    // ID estándar para el servidor SIP en GB28181 (El 200 indica Centro de Comando)
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
                    // El stream ya existe, lo cerramos y volvemos a intentar
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

    public Map<String, String> getPlaybackLinks(String channelSipId, String dvrIp, int dvrPort) {
        int zlmPort = openRtpServer(channelSipId);
        
        if (zlmPort == -1) {
            throw new ConflictException("No se pudo abrir puerto en ZLMediaKit");
        }

        // Armar el SDP
        String sdpData = 
                "v=0\r\n" +
                "o=" + SERVER_SIP_ID + " 0 0 IN IP4 " + sipLocalIp + "\r\n" +
                "s=Play\r\n" +
                "c=IN IP4 " + sipLocalIp + "\r\n" +
                "t=0 0\r\n" +
                "m=video " + zlmPort + " RTP/AVP 96\r\n" +
                "a=recvonly\r\n" +
                "a=rtpmap:96 PS/90000\r\n" +
                "y=0100000001\r\n";

        sendInviteToDvr(channelSipId, dvrIp, dvrPort, sdpData);

        return generatePlaybackLinks(channelSipId);
    }

    private void sendInviteToDvr(String channelSipId, String dvrIp, int dvrPort, String sdpData) {
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

            SubjectHeader subjectHeader = headerFactory.createSubjectHeader(channelSipId + ":0," + SERVER_SIP_ID + ":0");
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
        try {
            // 1. Cerrar puerto en ZLM (Forzar corte de recepción RTP)
            String closeUrl = String.format("%s/index/api/closeRtpServer?secret=%s&stream_id=%s", zlmApiUrl, zlmSecret, streamId);
            restTemplate.getForEntity(closeUrl, Map.class);
            System.out.println("ZLM cerró puerto RTP para " + streamId);
        } catch (Exception e) {
            System.err.println("Error al cerrar puerto RTP en ZLM: " + e.getMessage());
        }

        // 2. Mandar mensaje BYE a la cámara (Dialog SIP)
        javax.sip.Dialog dialog = miras.monitor.Zlmedia.Controller.ZlmController.activeDialogs.remove(streamId);
        if (dialog != null) {
            try {
                Request byeRequest = dialog.createRequest(Request.BYE);
                SipProvider sipProvider = sipConfig.getSipProviderUdp();
                ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
                dialog.sendRequest(ct);
                System.out.println("===== BYE MANUAL ENVIADO AL DVR (Cortando flujo) =====");
            } catch (Exception e) {
                System.err.println("Error al enviar el BYE al DVR: " + e.getMessage());
            }
        } else {
            System.out.println("No se encontró sesión SIP activa para " + streamId + " (quizás ya se cerró)");
        }
    }
}
