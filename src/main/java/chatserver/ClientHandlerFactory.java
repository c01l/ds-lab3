package chatserver;

import java.io.IOException;
import java.net.Socket;

public interface ClientHandlerFactory {
    /**
     * Create a new instance of a client handler for a new client
     * @param client the connection to the client
     * @return
     */
    Runnable createClientHandler(Socket client) throws IOException;
}
