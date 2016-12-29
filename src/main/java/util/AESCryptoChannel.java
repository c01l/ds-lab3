package util;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

/**
 * This class should be used as a Decorator for an existing {@link CommunicationChannel}.
 * It will allow the communication to be encrypted with AES.
 */
public class AESCryptoChannel implements CommunicationChannel, Decorated<CommunicationChannel> {

    private static Logger logger = Logger.getLogger("AESCryptoChannel");

    private final CommunicationChannel parent;

    private Cipher cipher;

    public AESCryptoChannel(CommunicationChannel parent, byte[] iv, byte[] key) throws InvalidKeyException {
        this.parent = parent;
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
    public InputStream getInputStream() throws IOException {
        final BufferedReader stream = new BufferedReader(new InputStreamReader(this.parent.getInputStream()));
        return new InputStream() {

            String outbuffer = new String();
            int pos;

            @Override
            public int read() throws IOException {
                if (outbuffer.isEmpty()) {
                    try {
                        outbuffer = decryptMsg(stream.readLine());
                        pos = 0;
                    } catch (BrokenMessageException e) {
                        logger.warning(e.getMessage()); // TODO check handling
                    }
                }

                return outbuffer.charAt(pos++);
            }
        };
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return new OutputStream() {
            StringBuilder sb = new StringBuilder();

            @Override
            public void write(int b) throws IOException {
                // read until \n
                if (b == 13) {
                    try {
                        parent.getOutputStream().write(encryptMsg(sb.toString() + "\n").getBytes());
                        sb = new StringBuilder();
                    } catch (BrokenMessageException e) {
                        logger.warning(e.getMessage()); // TODO check it
                    }
                } else {
                    sb.append((char) b);
                }
            }
        };
    }

    @Override
    public void close() throws IOException {
        this.parent.close();
    }

    @Override
    public CommunicationChannel getReal() {
        return this.parent;
    }

    /**
     * This function decrypts an incoming message.
     *
     * @param msg BASE64 encoded message
     * @return the decrypted message
     */
    private String decryptMsg(String msg) throws IOException, BrokenMessageException {
        BASE64Decoder decoder = new BASE64Decoder();
        byte[] data = decoder.decodeBuffer(msg);

        try {
            byte[] decrypted = this.cipher.doFinal(data);

            return new String(decrypted);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new BrokenMessageException(e);
        }
    }

    private String encryptMsg(String msg) throws IOException, BrokenMessageException {
        try {
            byte[] encrypted = this.cipher.doFinal(msg.getBytes());

            BASE64Encoder encoder = new BASE64Encoder();
            String encoded = encoder.encode(encrypted);

            return encoded;
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new BrokenMessageException(e);
        }
    }
}
