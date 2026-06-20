package miras.monitor.Zlmedia.Controller;

import javax.sip.DialogTerminatedEvent;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ZlmController implements SipListener {

    public static final Map<String, javax.sip.Dialog> activeDialogs = new ConcurrentHashMap<>();

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
            
            // Si el DVR contestó 200 OK a nuestro INVITE
            if (response.getStatusCode() == Response.OK && cseq.getMethod().equals(Request.INVITE)) {
                javax.sip.Dialog dialog = responseEvent.getDialog();
                if (dialog != null) {
                    // Hay que mandarle un ACK para confirmar que recibimos el OK, 
                    // si no el DVR asume error y no manda el video
                    Request ackRequest = dialog.createAck(cseq.getSeqNumber());
                    dialog.sendAck(ackRequest);
                    System.out.println("===== ACK ENVIADO AL DVR =====");
                    
                    // Extraer el canal SIP para guardar la conexión (Dialog) y poder mandarle BYE después
                    javax.sip.header.ToHeader toHeader = (javax.sip.header.ToHeader) response.getHeader(javax.sip.header.ToHeader.NAME);
                    String channelSipId = ((SipURI) toHeader.getAddress().getURI()).getUser();
                    activeDialogs.put(channelSipId, dialog);
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
