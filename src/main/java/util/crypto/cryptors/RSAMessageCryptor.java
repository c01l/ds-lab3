package util.crypto.cryptors;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;
import util.crypto.BrokenMessageException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

/**
 * This class allows to encrypt a communication channel with AES.
 */
public class RSAMessageCryptor implements MessageCryptor {
    private static final String RSAMODE = "RSA/NONE/OAEPWithSHA256AndMGF1Padding";

    private static final Logger logger = Logger.getLogger("RSAMessageCryptor");

    private Cipher cipher;
    private Key encryptionKey, decryptionKey;

    public RSAMessageCryptor(Key encryptionKey, Key decryptionKey) {
        try {
            this.cipher = Cipher.getInstance(RSAMODE);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            logger.warning("Failed to initialize cipher!");
            throw new AssertionError(e);
        }

        this.encryptionKey = encryptionKey;
        this.decryptionKey = decryptionKey;
    }

    @Override
    public String encrypt(String msg) throws BrokenMessageException {
        BASE64Encoder encoder = new BASE64Encoder();
        try {
            this.cipher.init(Cipher.ENCRYPT_MODE, this.encryptionKey);

            byte[] encrypted = this.cipher.doFinal(msg.getBytes());
            String encoded = encoder.encode(encrypted);

            logger.info("Encrypted to: " + encoded);

            return encoded;
        }catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            throw new BrokenMessageException(e);
        }
    }

    @Override
    public String decrypt(String msg) throws BrokenMessageException {
        BASE64Decoder decoder = new BASE64Decoder();
        try {
            this.cipher.init(Cipher.DECRYPT_MODE, this.decryptionKey);
            byte[] decoded = decoder.decodeBuffer(msg);

            byte[] decrypted = this.cipher.doFinal(decoded);

            String ret = new String(decrypted);

            logger.info("Decrypted to: " + ret);

            return ret;
        }catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException | IOException e) {
            throw new BrokenMessageException(e);
        }
    }
}
