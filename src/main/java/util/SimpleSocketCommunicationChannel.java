package util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.logging.Logger;


public class SimpleSocketCommunicationChannel implements CommunicationChannel {
    private static final Logger logger = Logger.getLogger("SimpleSocketCommunicationChannel");

    private Socket socket;

    public SimpleSocketCommunicationChannel(Socket socket) {
        this.socket = socket;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return this.socket.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return this.socket.getOutputStream();
    }

    @Override
    public void close() throws IOException {
        logger.info("Closing socket! (socket: " + this.socket + ")");
        this.socket.close();
    }
}
