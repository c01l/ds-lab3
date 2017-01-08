package util;

import org.bouncycastle.util.encoders.Base64;

import javax.crypto.Mac;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HMAC {
    private static final Logger logger = Logger.getLogger("HMAC");

    /**
     *
     * @param message
     * @param sharedSecret
     * @return
     */
    public static byte[] generateHMAC(String message, Key sharedSecret){
        Mac hMac = null;
        try {
            hMac = Mac.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            logger.log(Level.SEVERE, "Unable to load HMAC-Algorithm!");
        }

        try {
            hMac.init(sharedSecret);
        } catch (InvalidKeyException e) {
            logger.log(Level.SEVERE, "Unable to initialize HMAC with shared secret!");
        }

        hMac.update(message.getBytes());

        return Base64.encode(hMac.doFinal());
    }
}
