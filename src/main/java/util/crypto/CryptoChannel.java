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
            int pos = 0;

            @Override
            public int read() throws IOException {
                if (outbuffer.isEmpty() || pos >= outbuffer.length()) {
                    try {
                        logger.fine("Blocking...");
                        String line = stream.readLine();

                        if (line == null) {
                            return -1; // stream is dead!
                        }

                        outbuffer = cryptor.decrypt(line);
                        outbuffer += "\n";
                        pos = 0;
                    } catch (BrokenMessageException e) {
                        logger.warning(e.getMessage()); // TODO check handling
                        e.printStackTrace();
                        return -1;
                    }
                }

                char c = outbuffer.charAt(pos++);
                logger.finest("Returning: " + (int) c);
                return c;
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
                if (b == '\n') {
                    try {
                        BufferedWriter bWriter = new BufferedWriter(new OutputStreamWriter(parent.getOutputStream()));
                        bWriter.write(cryptor.encrypt(sb.toString()) + "\n");
                        bWriter.flush();
                        sb = new StringBuilder();
                    } catch (BrokenMessageException e) {
                        logger.warning(e.getMessage()); // TODO check it
                    }
                } else {
                    sb.append((char) b);
                    //logger.info("Write: " + (char) b);
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
