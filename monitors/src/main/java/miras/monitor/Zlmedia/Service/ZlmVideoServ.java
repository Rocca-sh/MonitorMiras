package miras.monitor.Zlmedia.Service;

import miras.monitor.Exceptions.Conflict.DvrRejectedException;
import miras.monitor.Exceptions.Conflict.DvrBusyException;
import miras.monitor.Exceptions.InternalServer.InternalServerException;
import miras.monitor.Exceptions.Timeout.DvrTimeoutException;
import miras.monitor.Exceptions.NotFound.NotFoundException;
import miras.monitor.Utils.RedisDvrService;
import miras.monitor.Zlmedia.Config.SipConfig;
import miras.monitor.Zlmedia.Repo.ZlmVideoRepo;
import miras.monitor.Zlmedia.Controller.ZlmController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.TransactionUnavailableException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import miras.monitor.Exceptions.InternalServer.InternalServerException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ZlmVideoServ {

    @Autowired
    private SipConfig sipConfig;

    @Autowired
    private RedisDvrService redisDvrService;

    @Autowired
    private ZlmVideoRepo zlmVideoRepo;

    @Value("${sip.local.ip:127.0.0.1}")
    private String sipLocalIp;

    private final String SERVER_SIP_ID = "34020000002000000001";

    // Mapas para manejar la Fila de comandos (Queue) POR cada DVR Fisico
    private static final Map<String, ExecutorService> dvrCommandQueues = new ConcurrentHashMap<>();
    private static final Map<String, String> streamToQueueKey = new ConcurrentHashMap<>();

    private ExecutorService getDvrQueue(String queueKey) {
        return dvrCommandQueues.computeIfAbsent(queueKey, k -> Executors.newSingleThreadExecutor());
    }

    public Map<String, String> getPlaybackLinks(String channelSipId, String dvrIp, int dvrPort, int quality) {
        String queueKey = dvrIp + ":" + dvrPort;
        streamToQueueKey.put(channelSipId, queueKey);
        ExecutorService queue = getDvrQueue(queueKey);

        CompletableFuture<Map<String, String>> resultFuture = new CompletableFuture<>();

        queue.submit(() -> {
            try {
                if (ZlmController.activeDialogs.containsKey(channelSipId)) {
                    System.out.println("====== REUTILIZANDO LINK DE CAMARA " + channelSipId + " ======");
                    resultFuture.complete(zlmVideoRepo.generatePlaybackLinks(channelSipId));
                    return;
                }

                int zlmPort = zlmVideoRepo.openRtpServer(channelSipId);
                String sdpData = generateSdpData(zlmPort);

                CompletableFuture<Void> future = new CompletableFuture<>();
                ZlmController.pendingFutures.put(channelSipId, future);

                try {
                    sendInviteToDvr(channelSipId, dvrIp, dvrPort, sdpData, quality);
                    future.get(10, TimeUnit.SECONDS); // Esperar que el DVR conteste (200 OK)
                    
                    resultFuture.complete(zlmVideoRepo.generatePlaybackLinks(channelSipId));
                    
                    // Obligar al cajero a tomarse un respiro de 1 segundo para no sofocar al DVR
                    Thread.sleep(1000);
                } catch (TimeoutException | InterruptedException | ExecutionException e) {
                    if (e instanceof ExecutionException && e.getCause() instanceof DvrRejectedException) {
                        resultFuture.completeExceptionally(e.getCause());
                    } else if (e instanceof ExecutionException && e.getCause() instanceof DvrBusyException) {
                        resultFuture.completeExceptionally(e.getCause());
                    } else {
                        resultFuture.completeExceptionally(new DvrTimeoutException("El dispositivo o camara (" + channelSipId + ") no contesto en 10 segundos."));
                    }
                } finally {
                    ZlmController.pendingFutures.remove(channelSipId);
                }
            } catch (Exception e) {
                if (e instanceof InternalServerException) {
                    resultFuture.completeExceptionally(e);
                } else {
                    resultFuture.completeExceptionally(new InternalServerException("Error inesperado al pedir el stream", e));
                }
            }
        });

        try {
            // El usuario web espera hasta 15 segundos a que la fila avance y el DVR responda
            return resultFuture.get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e instanceof ExecutionException && e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new InternalServerException("El DVR tardo demasiado en responder debido a la fila de comandos.", e);
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
               "a=streamprofile:1\r\n" +
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
            Address toAddress = addressFactory.createAddress(toUri);
            ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

            SipURI fromUri = addressFactory.createSipURI(SERVER_SIP_ID, sipLocalIp);
            fromUri.setPort(5060);
            Address fromAddress = addressFactory.createAddress(fromUri);
            FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, "catalog" + System.currentTimeMillis());

            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.MESSAGE);

            MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

            Request request = messageFactory.createRequest(
                    toUri, Request.MESSAGE, callIdHeader, cSeqHeader, fromHeader,
                    toHeader, java.util.Collections.singletonList(headerFactory.createViaHeader(sipLocalIp, 5060, "udp", null)),
                    maxForwards);

            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("Application", "MANSCDP+xml");
            request.setContent(xmlContent, contentTypeHeader);

            sipProvider.sendRequest(request);
            System.out.println("===== PETICION DE CATALOGO ENVIADA AL DVR " + dvrSipId + " =====");

            // Esperamos hasta 5 segundos a que el DVR responda el catalogo y ZlmController lo guarde
            int waitTime = 0;
            while (waitTime < 5000) {
                String channels = redisDvrService.getChannels(dvrSipId);
                if (channels != null) {
                    try { Thread.sleep(1000); } catch (Exception e) {} // Dar tiempo a que lleguen y se procesen los demas paquetes UDP
                    return redisDvrService.getChannels(dvrSipId);
                }
                try { Thread.sleep(200); } catch (Exception e) {}
                waitTime += 200;
            }

            redisDvrService.setDvrOffline(dvrSipId);
            throw new DvrTimeoutException("El dispositivo (" + dvrSipId + ") no respondio al catalogo. Puede estar apagado o fuera de linea.");
        } catch (DvrTimeoutException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new InternalServerException("Error al solicitar el catalogo al DVR", e);
        }
    }

    private void sendInviteToDvr(String channelSipId, String dvrIp, int dvrPort, String sdpData, int quality) {
        try {
            SipProvider sipProvider = sipConfig.getSipProviderUdp();
            SipFactory sipFactory = SipFactory.getInstance();
            AddressFactory addressFactory = sipFactory.createAddressFactory();
            HeaderFactory headerFactory = sipFactory.createHeaderFactory();
            MessageFactory messageFactory = sipFactory.createMessageFactory();

            SipURI toUri = addressFactory.createSipURI(channelSipId, dvrIp);
            toUri.setPort(dvrPort);
            Address toAddress = addressFactory.createAddress(toUri);
            ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

            SipURI fromUri = addressFactory.createSipURI(SERVER_SIP_ID, sipLocalIp);
            fromUri.setPort(5060);
            Address fromAddress = addressFactory.createAddress(fromUri);
            FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, "tag" + System.currentTimeMillis());

            SipURI requestUri = addressFactory.createSipURI(channelSipId, dvrIp);
            requestUri.setPort(dvrPort);

            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);
            MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

            ArrayList<ViaHeader> viaHeaders = new ArrayList<>();
            ViaHeader viaHeader = headerFactory.createViaHeader(sipLocalIp, 5060, "udp", "z9hG4bK" + UUID.randomUUID().toString().replace("-", ""));
            viaHeaders.add(viaHeader);

            Request request = messageFactory.createRequest(requestUri, Request.INVITE, callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);

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
            
        } catch (TransactionUnavailableException e) {
            throw new NotFoundException("Canal no encontrado o el DVR rechazo la conexion (Offline o SIP ID incorrecto).");
        } catch (Exception e) {
            System.err.println("Error al construir/enviar el INVITE:");
            e.printStackTrace();
            throw new InternalServerException("Error al enviar INVITE al DVR");
        }
    }

    public void stopStream(String streamId) {
        System.out.println("Solicitud de stop manual recibida para " + streamId);
        closeStream(streamId, "STOP_MANUAL");
    }

    public void closeStream(String streamId, String source) {
        Dialog dialog = ZlmController.activeDialogs.remove(streamId);

        // Siempre mandamos a cerrar el puerto RTP en ZLM para asfixiar fantasmas
        zlmVideoRepo.closeRtpServer(streamId);

        if (dialog != null) {
            String queueKey = streamToQueueKey.get(streamId);
            ExecutorService queue = queueKey != null ? getDvrQueue(queueKey) : Executors.newSingleThreadExecutor();

            queue.submit(() -> {
                try {
                    Request byeRequest = dialog.createRequest(Request.BYE);
                    SipProvider sipProvider = sipConfig.getSipProviderUdp();
                    ClientTransaction ct = sipProvider.getNewClientTransaction(byeRequest);
                    dialog.sendRequest(ct);
                    System.out.println("===== BYE ENVIADO AL DVR [" + source + "] (" + streamId + ") =====");
                    
                    // Obligar al cajero a tomarse un respiro de 1 segundo después del BYE
                    Thread.sleep(1000); 
                } catch (Exception e) {
                    System.err.println("Error al enviar BYE al DVR: " + e.getMessage());
                }
            });
            streamToQueueKey.remove(streamId);
        }
    }

    public void rebootDvr(String dvrSipId, String dvrIp, int dvrPort) {
        try {
            SipProvider sipProvider = sipConfig.getSipProviderUdp();
            SipFactory sipFactory = SipFactory.getInstance();
            AddressFactory addressFactory = sipFactory.createAddressFactory();
            HeaderFactory headerFactory = sipFactory.createHeaderFactory();
            MessageFactory messageFactory = sipFactory.createMessageFactory();

            String xmlContent = "<?xml version=\"1.0\" encoding=\"GB2312\"?>\r\n" +
                                "<Control>\r\n" +
                                "<CmdType>DeviceControl</CmdType>\r\n" +
                                "<SN>" + (int)(Math.random() * 1000) + "</SN>\r\n" +
                                "<DeviceID>" + dvrSipId + "</DeviceID>\r\n" +
                                "<TeleBoot>Boot</TeleBoot>\r\n" +
                                "</Control>\r\n";
            
            SipURI toUri = addressFactory.createSipURI(dvrSipId, dvrIp);
            toUri.setPort(dvrPort);
            Address toAddress = addressFactory.createAddress(toUri);
            ToHeader toHeader = headerFactory.createToHeader(toAddress, null);

            SipURI fromUri = addressFactory.createSipURI(SERVER_SIP_ID, sipLocalIp);
            fromUri.setPort(5060);
            Address fromAddress = addressFactory.createAddress(fromUri);
            FromHeader fromHeader = headerFactory.createFromHeader(fromAddress, "boot" + System.currentTimeMillis());

            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.MESSAGE);
            MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);

            Request request = messageFactory.createRequest(
                    toUri, Request.MESSAGE, callIdHeader, cSeqHeader, fromHeader,
                    toHeader, java.util.Collections.singletonList(headerFactory.createViaHeader(sipLocalIp, 5060, "udp", null)),
                    maxForwards);

            ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("Application", "MANSCDP+xml");
            request.setContent(xmlContent, contentTypeHeader);

            sipProvider.sendRequest(request);
            System.out.println("===== COMANDO TELEBOOT ENVIADO AL DVR " + dvrSipId + " =====");
        } catch (Exception e) {
            throw new InternalServerException("Error al enviar comando de reinicio al DVR", e);
        }
    }
}
