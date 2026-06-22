package miras.monitor.Zlmedia.Controller;

import javax.sip.DialogTerminatedEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import miras.monitor.Utils.RedisDvrService;
import miras.monitor.Exceptions.Conflict.DvrRejectedException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Component
public class ZlmController implements SipListener {

    public static final Map<String, javax.sip.Dialog> activeDialogs = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> pendingStreams = new ConcurrentHashMap<>();
    public static final java.util.Set<String> stoppedStreams = ConcurrentHashMap.newKeySet();
    public static final Map<String, CompletableFuture<Void>> pendingFutures = new ConcurrentHashMap<>();

    @Autowired
    private RedisDvrService redisDvrService;

    @Override
    public void processRequest(RequestEvent requestEvent) {
        Request request = requestEvent.getRequest();
        
        if (request.getMethod().equals(Request.REGISTER)) {
            handleRegister(requestEvent);
        } else if (request.getMethod().equals(Request.MESSAGE)) {
            handleMessage(requestEvent);
        }
    }

    private void handleRegister(RequestEvent requestEvent) {
        try {
            Request request = requestEvent.getRequest();
            SipProvider provider = (SipProvider) requestEvent.getSource();
            
            // 1. Extraer el SIP ID del Header "From"
            FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
            SipURI sipUri = (SipURI) fromHeader.getAddress().getURI();
            String sipId = sipUri.getUser();
            
            // 2. Extraer IP y Puerto REAL del DVR (Mejor que Contact por el NAT)
            ViaHeader viaHeader = (ViaHeader) request.getHeader(ViaHeader.NAME);
            if (viaHeader != null) {
                String dvrIp = viaHeader.getReceived();
                if (dvrIp == null || dvrIp.equals("0.0.0.0")) {
                    dvrIp = viaHeader.getHost();
                }
                
                int dvrPort = viaHeader.getRPort();
                if (dvrPort <= 0) dvrPort = viaHeader.getPort();
                if (dvrPort <= 0) dvrPort = 5060;
                
                // 3. Guardar en Redis con número de canales por defecto (1) hasta que recibamos el Catalog
                redisDvrService.registerDvr(sipId, dvrIp, dvrPort, 1);
                System.out.println("====== DVR " + sipId + " CONECTADO (" + dvrIp + ":" + dvrPort + ") ======");
            }

            // 4. Enviar la respuesta 200 OK
            MessageFactory messageFactory = SipFactory.getInstance().createMessageFactory();
            Response response = messageFactory.createResponse(200, request);
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = provider.getNewServerTransaction(request);
            }
            st.sendResponse(response);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleMessage(RequestEvent requestEvent) {
        try {
            Request request = requestEvent.getRequest();
            SipProvider provider = (SipProvider) requestEvent.getSource();

            // 1. Responder 200 OK para confirmar recibo
            MessageFactory messageFactory = SipFactory.getInstance().createMessageFactory();
            Response response = messageFactory.createResponse(200, request);
            ServerTransaction st = requestEvent.getServerTransaction();
            if (st == null) {
                st = provider.getNewServerTransaction(request);
            }
            st.sendResponse(response);

            // 2. Revisar el contenido para ver si es un Latido (Keepalive)
            byte[] rawContent = request.getRawContent();
            if (rawContent != null) {
                String xmlString = new String(rawContent, "GB2312"); 
                
                // Extraer el SIP ID para saber quién mandó el mensaje
                FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
                String sipId = ((SipURI) fromHeader.getAddress().getURI()).getUser();
                
                if (xmlString.contains("<CmdType>Keepalive</CmdType>")) {
                    // Extraer IP y Puerto REAL del DVR para asegurar que se registre incluso si Redis se borró
                    ViaHeader viaHeader = (ViaHeader) request.getHeader(ViaHeader.NAME);
                    if (viaHeader != null) {
                        String dvrIp = viaHeader.getReceived();
                        if (dvrIp == null || dvrIp.equals("0.0.0.0")) dvrIp = viaHeader.getHost();
                        
                        int dvrPort = viaHeader.getRPort();
                        if (dvrPort <= 0) dvrPort = viaHeader.getPort();
                        if (dvrPort <= 0) dvrPort = 5060;
                        
                        // Si la llave no existe (ej. reinicio), registerDvr la crea y avisa al Frontend. 
                        // Por defecto asumimos 1 canal, hasta poder procesar el Catalog
                        redisDvrService.registerDvr(sipId, dvrIp, dvrPort, 1);
                    }
                } 
                else if (xmlString.contains("<CmdType>Catalog</CmdType>")) {
                    System.out.println("====== CATÁLOGO RECIBIDO DEL DVR " + sipId + " ======");
                    // System.out.println(xmlString); // Imprimir el XML crudo para depurar
                    try {
                        List<Map<String, String>> channels = new ArrayList<>();
                        Pattern itemPattern = Pattern.compile("<Item>(.*?)</Item>", Pattern.DOTALL);
                        Matcher itemMatcher = itemPattern.matcher(xmlString);
                        
                        while (itemMatcher.find()) {
                            String itemXml = itemMatcher.group(1);
                            
                            Pattern idPattern = Pattern.compile("<DeviceID>(.*?)</DeviceID>");
                            Matcher idMatcher = idPattern.matcher(itemXml);
                            String deviceId = idMatcher.find() ? idMatcher.group(1).trim() : "";
                            
                            Pattern namePattern = Pattern.compile("<Name>(.*?)</Name>");
                            Matcher nameMatcher = namePattern.matcher(itemXml);
                            
                            Pattern statusPattern = Pattern.compile("<Status>(.*?)</Status>");
                            Matcher statusMatcher = statusPattern.matcher(itemXml);
                            String name = nameMatcher.find() ? nameMatcher.group(1).trim() : "Desconocido";
                            String status = statusMatcher.find() ? statusMatcher.group(1).trim() : "OFF";
                            
                            if (!deviceId.isEmpty() && !deviceId.equals(sipId)) {
                                if (deviceId.length() < 20 && sipId.length() == 20) {
                                    try {
                                        String base = sipId.substring(0, 10);
                                        deviceId = base + "131" + "0" + String.format("%06d", Integer.parseInt(deviceId) + 1);
                                        System.out.println("ID reconstruido: " + deviceId);
                                    } catch (NumberFormatException e) {
                                        // Ignore parsing error, keep original ID
                                    }
                                }

                                Map<String, String> ch = new HashMap<>();
                                ch.put("id", deviceId);
                                ch.put("name", name);
                                ch.put("status", status);
                                channels.add(ch);
                            }
                        }
                        
                        ObjectMapper mapper = new ObjectMapper();
                        String existingJson = redisDvrService.getChannels(sipId);
                        if (existingJson != null) {
                            List<Map<String, String>> existing = mapper.readValue(existingJson, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>(){});
                            existing.addAll(channels);
                            channels = existing;
                        }
                        
                        String jsonChannels = mapper.writeValueAsString(channels);
                        redisDvrService.saveChannels(sipId, jsonChannels);
                        System.out.println("Canales guardados: " + channels.size());
                        
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        try {
            Response response = responseEvent.getResponse();
            CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
            
            if (cseq.getMethod().equals(Request.INVITE)) {
                javax.sip.header.ToHeader toHeader = (javax.sip.header.ToHeader) response.getHeader(javax.sip.header.ToHeader.NAME);
                String channelSipId = ((SipURI) toHeader.getAddress().getURI()).getUser();

                if (response.getStatusCode() == Response.OK) {
                    javax.sip.Dialog dialog = responseEvent.getDialog();
                    if (dialog != null) {
                        Request ackRequest = dialog.createAck(cseq.getSeqNumber());
                        dialog.sendAck(ackRequest);
                        System.out.println("===== ACK ENVIADO AL DVR =====");
                        
                        pendingStreams.remove(channelSipId);
                        
                        if (stoppedStreams.contains(channelSipId)) {
                            stoppedStreams.remove(channelSipId);
                            
                            SipProvider provider = (SipProvider) responseEvent.getSource();
                            Request byeRequest = dialog.createRequest(Request.BYE);
                            javax.sip.ClientTransaction ct = provider.getNewClientTransaction(byeRequest);
                            dialog.sendRequest(ct);
                            System.out.println("===== BYE ENVIADO INMEDIATAMENTE AL DVR (Cancelación Rápida) =====");
                            
                            CompletableFuture<Void> cancelledFuture = pendingFutures.remove(channelSipId);
                            if (cancelledFuture != null) {
                                cancelledFuture.completeExceptionally(
                                    new IllegalStateException("Stream cancelado antes de confirmar (" + channelSipId + ")"));
                            }
                        } else {
                            activeDialogs.put(channelSipId, dialog);
                            
                            CompletableFuture<Void> future = pendingFutures.remove(channelSipId);
                            if (future != null) {
                                future.complete(null);
                            }
                        }
                    }
                } else if (response.getStatusCode() > 200) {
                    System.err.println("===== EL DVR RECHAZÓ EL INVITE PARA " + channelSipId + " CON CÓDIGO: " + response.getStatusCode() + " =====");
                    pendingStreams.remove(channelSipId);
                    CompletableFuture<Void> future = pendingFutures.remove(channelSipId);
                    if (future != null) {
                        future.completeExceptionally(new DvrRejectedException("El DVR rechazó la conexión o la cámara está ocupada. Código SIP: " + response.getStatusCode()));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {}

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {}

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {}

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {}
}
