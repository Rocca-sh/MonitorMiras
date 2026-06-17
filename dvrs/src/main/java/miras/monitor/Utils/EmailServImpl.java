package miras.monitor.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailServImpl implements EmailServ {

    private static final Logger logger = LoggerFactory.getLogger(EmailServImpl.class);

    @Override
    public void sendCode(String email, String code) {
        // TODO: Configurar un proveedor real de SMTP (como SendGrid, AWS SES o Gmail)
        // Por ahora, imprimimos el código en la consola para poder hacer las pruebas de login
        logger.info("==========================================================");
        logger.info("FINGIENDO ENVÍO DE EMAIL A: {}", email);
        logger.info("CÓDIGO DE VERIFICACIÓN: {}", code);
        logger.info("==========================================================");
    }
}
