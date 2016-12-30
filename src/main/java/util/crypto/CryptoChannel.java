package util.crypto;

import util.CommunicationChannel;
import util.Decorated;
import util.crypto.cryptors.MessageCryptor;

import java.io.*;
import java.security.InvalidKeyException;
import java.util.logging.Logger;

/**
 * This class should be used as a Decorator for an existing {@link CommunicationChannel}.
 * It will allow the communication to be encrypted with AES.
 */
public class CryptoChannel implements CommunicationChannel, Decorated<CommunicationChannel> {

    private static Logger logger = Logger.getLogger("CryptoChannel");

    private final CommunicationChannel parent;
    private final MessageCryptor cryptor;

    public CryptoChannel(CommunicationChannel parent, MessageCryptor cryptor) throws InvalidKeyException {
        this.parent = parent;
        this.cryptor = cryptor;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        final BufferedReader stream = new BufferedReader(new InputStreamReader(this.parent.getInputStream()));
        return new InputStream() {

            String outbuffer = "";
            int pos;

            @Override
            public int read() throws IOException {
                if (outbuffer.isEmpty()) {
                    try {
                        outbuffer = cryptor.decrypt(stream.readLine());
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
                        parent.getOutputStream().write(cryptor.encrypt(sb.toString() + "\n").getBytes());
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

}