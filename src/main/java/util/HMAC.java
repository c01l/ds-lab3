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
     * This method generates the HMAC for a given message and a shared secret. The return is Base64 encoded.
     *
     * @param message      Message that will be used to generate the HMAC
     * @param sharedSecret The used shared secret
     * @return The HMAC is returned in a Base6-Encoding
     */
    public static byte[] generateHMAC(String message, Key sharedSecret) {
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
