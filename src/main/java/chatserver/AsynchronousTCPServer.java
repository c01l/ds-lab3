package chatserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link AsynchronousTCPServer} manages a {@link ServerSocket} and Executes a new thread for each client in
 * case a new TCP Connection is opened.
 */
public class AsynchronousTCPServer extends Thread {

    private static final Logger LOGGER = Logger.getLogger("AsynchronousTCPServer");
    static { LOGGER.setLevel(Level.WARNING); }    

    private final int port;
    private ServerSocket socket;
    private ClientHandlerFactory factory;
    private final ExecutorService pool;

    public AsynchronousTCPServer(int port, ClientHandlerFactory clientHandlerFactory) {
        this.port = port;
        this.pool = Executors.newCachedThreadPool();
        this.factory = clientHandlerFactory;
    }

    @Override
    public synchronized void start() {
        try {
            this.socket = new ServerSocket(this.port);
            super.start();

            LOGGER.info("Server started on port " + this.port);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start TCP Server on port " + this.port, e);
        }
    }

    @Override
    public void run() {
        while (!this.isInterrupted()) {
            try {
                LOGGER.info("Listening...");
                Socket client = this.socket.accept();

                LOGGER.info("Client " + client.getInetAddress().getHostAddress() + " connected!");

                pool.execute(this.factory.createClientHandler(client));
            } catch (IOException e) {
                if (this.socket.isClosed()) {
                    // Thats why it dies...
                    break;
                } else {
                    // Unwanted Exception
                    LOGGER.warning("Exception: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        LOGGER.info("AsynchronousTCPServer stopped taking requests! Closing...");

        this.interrupt();
    }

    @Override
    public void interrupt() {
        try {
            this.socket.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to close tcp socket");
        }

        this.pool.shutdown();

        super.interrupt();
    }
}
