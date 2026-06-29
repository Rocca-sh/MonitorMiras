package miras.monitor.Zlmedia.Config;

import java.util.Properties;
import jakarta.annotation.PostConstruct;
import javax.sip.ListeningPoint;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import miras.monitor.Zlmedia.Controller.ZlmController;

@Configuration
public class SipConfig {

    @Autowired
    private ZlmController zlmController;

    private SipStack sipStack;
    private SipProvider sipProviderUdp;
    private SipProvider sipProviderTcp;

    @PostConstruct
    public void init() {
        try {
            // 1. Obtener la fabrica de SIP
            SipFactory sipFactory = SipFactory.getInstance();
            sipFactory.setPathName("gov.nist"); // Usamos la implementacion estandar (Reference Implementation)

            // Configurar propiedades del Stack SIP
            Properties properties = new Properties();
            properties.setProperty("javax.sip.STACK_NAME", "MirasSipServer");
            // Nivel de Logs internos de la libreria (32 es maximo debug, 0 es apagado)
            properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0"); 
            properties.setProperty("gov.nist.javax.sip.SERVER_LOG", "sip_server_log.txt");
            properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "sip_debug_log.txt");

            // Crear el motor principal (Stack)
            sipStack = sipFactory.createSipStack(properties);

            // Crear los puntos de escucha en todas las IPs (0.0.0.0) en el puerto 5060
            ListeningPoint udp = sipStack.createListeningPoint("0.0.0.0", 5060, "udp");
            ListeningPoint tcp = sipStack.createListeningPoint("0.0.0.0", 5060, "tcp");

            // Crear los "proveedores" (proveen el servicio de red)
            sipProviderUdp = sipStack.createSipProvider(udp);
            sipProviderTcp = sipStack.createSipProvider(tcp);

            
            sipProviderUdp.addSipListener(zlmController);
            sipProviderTcp.addSipListener(zlmController);

            System.out.println("==================================================");
            System.out.println(" Servidor SIP (GB28181) iniciado y escuchando en:");
            System.out.println(" -> 0.0.0.0:5060 (UDP)");
            System.out.println(" -> 0.0.0.0:5060 (TCP)");
            System.out.println("==================================================");

        } catch (Exception e) {
            System.err.println("¡Error critico al intentar iniciar el servidor SIP!");
            e.printStackTrace();
        }
    }

    public SipProvider getSipProviderUdp() {
        return sipProviderUdp;
    }
}
