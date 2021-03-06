package util.crypto.cryptors;

import org.bouncycastle.util.encoders.Base64;
import util.crypto.BrokenMessageException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

/**
 * This class allows to encrypt a communication channel with AES.
 */
public class AESMessageCryptor implements MessageCryptor {
    private static Logger logger = Logger.getLogger("AESMessageCryptor");

    private Cipher cipher;

    public AESMessageCryptor(byte[] iv, byte[] key) throws InvalidKeyException {
        try {
            this.cipher = Cipher.getInstance("AES/CTR/NoPadding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            logger.warning("Failed to initialize cipher!");
            throw new AssertionError(e);
        }

        IvParameterSpec ivPs = new IvParameterSpec(iv);
        SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");

        try {
            this.cipher.init(Cipher.DECRYPT_MODE, skeySpec, ivPs);
        } catch (InvalidKeyException e) {
            logger.warning("Wrong key!");
            throw e;
        } catch (InvalidAlgorithmParameterException e) {
            logger.warning("Invalid Arguments for the cipher.");
            throw new AssertionError(e);
        }
    }

    @Override
    public String decrypt(String msg) throws BrokenMessageException {
        try {
            byte[] data = Base64.decode(msg);
            byte[] decrypted = this.cipher.doFinal(data);

            String ret = new String(decrypted);
            logger.info("Decrypted to: " + ret);
            return ret;
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new BrokenMessageException(e);
        }
    }

    @Override
    public String encrypt(String msg) throws BrokenMessageException {
        try {
            byte[] encrypted = this.cipher.doFinal(msg.getBytes());

            String encoded = new String(Base64.encode(encrypted));

            logger.info("Encrypted to: " + encoded);

            return encoded;
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new BrokenMessageException(e);
        }
    }
}
