package xyz.rebasing.rebot.plugin.dimep.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Random;

/**
 * Utils class
 */
public abstract class Utils {

    /**
     * Base64 encoder
     *
     * @return base64 encoded string
     * @param encoded to encode
     */
    public static String base64decode(String encoded) {
        byte[] decodedBytes = Base64.getDecoder().decode(encoded);
        return new String(decodedBytes);
    }

    /**
     * return the date in the pattern required by the DIMEP API
     *
     * @return formated date, if no parameter passed, just return the instant.
     * @param current the current date
     */
    public static String formatDate(Instant current) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:00 - EEEE - 'OK'")
                .withZone(ZoneId.of("America/Sao_Paulo"));
        return formatter.format(current);
    }

    /**
     * Returns a random number between 45 and 55
     * @return a random number between 45 and 55
     */
    public static int getRandomNumberBetween45And55() {
        Random random = new Random();
        return 45 + random.nextInt(15); // 21 because nextInt is exclusive of the upper bound
    }

}

