package miras.monitor.Zlmedia.Repo;

import miras.monitor.Zlmedia.Controller.ZlmController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestTemplate;
import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import miras.monitor.Exceptions.InternalServer.InternalServerException;

@Repository
public class ZlmVideoRepo {

    @Value("${zlm.api.url:http://127.0.0.1:80}")
    private String zlmApiUrl;

    @Value("${zlm.api.secret:change-me-zlm-secret}")
    private String zlmSecret;

    @Value("${zlm.public.ip:127.0.0.1}")
    private String zlmPublicIp;

    private final RestTemplate restTemplate = new RestTemplate();

    public int openRtpServer(String streamId) {
        try {
            String url = String.format("%s/index/api/openRtpServer?secret=%s&port=0&enable_tcp=1&stream_id=%s",
                    zlmApiUrl, zlmSecret, streamId);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                int code = (Integer) body.get("code");
                
                if (code == 0) {
                    int assignedPort = (Integer) body.get("port");
                    System.out.println("ZLM abrio puerto RTP: " + assignedPort + " para " + streamId);
                    return assignedPort;
                } else if (code == -300) {
                    System.out.println("El stream " + streamId + " ya existia en ZLM. Cerrando y reintentando...");
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
            throw new InternalServerException("No se pudo abrir puerto en ZLMediaKit: " + e.getMessage(), e);
        }
        throw new InternalServerException("ZLMediaKit respondio pero no asigno el puerto para " + streamId);
    }

    public void closeRtpServer(String streamId) {
        try {
            String url = String.format("%s/index/api/closeRtpServer?secret=%s&stream_id=%s", zlmApiUrl, zlmSecret, streamId);
            restTemplate.getForEntity(url, Map.class);
            System.out.println("ZLM cerro puerto RTP para " + streamId);
        } catch (Exception e) {
            System.err.println("Error al cerrar puerto RTP en ZLM: " + e.getMessage());
        }
    }

    @PostConstruct
    public void syncWithZlmOnStartup() {
        try {
            System.out.println("Sincronizando estado con ZLMediaKit en el arranque (Limpieza profunda)...");
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
                                closeRtpServer(streamId);
                                System.out.println("Stream fantasma eliminado de ZLM en el arranque: " + streamId);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("No se pudo sincronizar con ZLM en el arranque: " + e.getMessage());
        }
    }

    public Map<String, String> generatePlaybackLinks(String streamId) {
        Map<String, String> links = new HashMap<>();
        String app = "rtp";

        links.put("ws_flv", "ws://" + zlmPublicIp + "/" + app + "/" + streamId + ".live.flv");
        links.put("http_flv", "http://" + zlmPublicIp + "/" + app + "/" + streamId + ".live.flv");
        links.put("hls", "http://" + zlmPublicIp + "/" + app + "/" + streamId + "/hls.m3u8");
        links.put("webrtc", "ws://" + zlmPublicIp + "/index/api/webrtc?app=" + app + "&stream=" + streamId + "&type=play");
        links.put("rtsp", "rtsp://" + zlmPublicIp + "/" + app + "/" + streamId);

        return links;
    }
}
