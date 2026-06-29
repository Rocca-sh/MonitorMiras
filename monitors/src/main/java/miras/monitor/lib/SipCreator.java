package miras.monitor.lib;

import java.util.concurrent.ThreadLocalRandom;

public class SipCreator {

    public static String genOrgSip(long sequentialNumber) {
        // Generar un numero aleatorio de 7 digitos (entre 1000000 y 9999999)
        long prefix = ThreadLocalRandom.current().nextLong(1000000L, 10000000L);
        // Concatenar con los ultimos 3 digitos secuenciales formateados a ceros a la izquierda
        String suffix = String.format("%03d", sequentialNumber);
        return prefix + suffix;
    }

    public static String getOrgIdFromSip(String sipId) {
        if (sipId != null && sipId.length() >= 10) {
            return sipId.substring(0, 10);
        }
        return null;
    }
}
