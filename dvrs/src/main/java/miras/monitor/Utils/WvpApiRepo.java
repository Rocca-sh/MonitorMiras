package miras.monitor.Utils;

import org.springframework.stereotype.Repository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.CookieManager;
import java.net.CookiePolicy;

@Repository
public class WvpApiRepo {

    private final HttpClient httpClient;
    private final String wvpBaseUrl = "http://localhost:18080";
    
    // Contraseña MD5 de "admin" (Por defecto en WVP-Pro)
    private final String wvpUser = "admin";
    private final String wvpPassMd5 = "21232f297a57a5a743894a0e4a801fc3";

    public WvpApiRepo() {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        
        // El HttpClient mantendrá la cookie de sesión automáticamente
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .build();
    }

    public boolean login() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(wvpBaseUrl + "/api/user/login?username=" + wvpUser + "&password=" + wvpPassMd5))
                    .GET()
                    .build();
                    
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body().contains("\"code\":0");
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public String getPlayLinks(String deviceId, String channelId) {
        try {
            String cleanDeviceId = deviceId != null ? deviceId.trim() : "";
            String cleanChannelId = channelId != null ? channelId.trim() : "";
            String actualChannelId = (!cleanChannelId.isEmpty()) ? cleanChannelId : cleanDeviceId;
            
            // Solicitamos los links. Usamos el deviceId y el channelId correcto
            String url = wvpBaseUrl + "/api/play/start/" + cleanDeviceId + "/" + actualChannelId + "?setup=TCP";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                    
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            // Si WVP nos responde que no estamos logueados (-1)
            if (body.contains("-1") || body.contains("请登录")) {
                if (login()) {
                    // Si el login es exitoso, repetimos la petición de inmediato
                    response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    body = response.body();
                } else {
                    throw new RuntimeException("No se pudo iniciar sesión como admin en WVP-Pro");
                }
            }
            return body;
        } catch (Exception e) {
            throw new RuntimeException("Error al comunicarse con WVP: " + e.getMessage());
        }
    }
}
