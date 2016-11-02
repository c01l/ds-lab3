package client;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PrivateMessageReceiver implements Runnable {

    private static final Logger logger = Logger.getLogger("PrivateMessageReceiver");

    private final int port;
    private ServerSocket socket;
    private PrintStream userOutputStream;
    private ExecutorService pool;

    public PrivateMessageReceiver(int port, PrintStream userOutputStream) throws IOException {
        this.port = port;
        this.userOutputStream = userOutputStream;

        this.socket = new ServerSocket(port);
        this.pool = Executors.newCachedThreadPool();
    }

    @Override
    public void run() {

        while (!this.socket.isClosed()) {
            logger.fine("Listening on " + this.port);
            try {
                Socket client = this.socket.accept();
                logger.info("Got new client!");

                pool.execute(new ClientHandler(client));
            } catch (IOException e) {
                if (this.socket.isClosed()) {
                    // just quit
                    break;
                }

                logger.log(Level.SEVERE, "Failed accepting new client: " + e.getMessage());
            }
        }

        this.shutdown();
    }

    public void shutdown() {
        try {
            this.socket.close();
        } catch(IOException e) {
            logger.warning("Failed to close socket: " + e.getMessage());
        }

        this.pool.shutdown();
    }

    private class ClientHandler implements Runnable {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            try {
                // open streams
                BufferedReader in = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
                PrintWriter out = new PrintWriter(this.socket.getOutputStream());

                logger.fine("reading users message");

                String line = in.readLine();
                logger.info("User sent: '" + line + "'");
                // write line to output stream
                userOutputStream.println(line);

                // respond with "!ack"
                logger.fine("Sending !ack...");
                out.println("!ack");
                out.flush();

                logger.fine("Sent !ack");

            } catch (IOException ex) {
                logger.warning("Error while handling client: " + ex.getMessage());
            } finally {
                try {
                    this.socket.shutdownInput();
                } catch (IOException e) {
                    logger.warning("Failed to close input: " + e.getMessage());
                }
                try {
                    this.socket.shutdownOutput();
                } catch (IOException e) {
                    logger.warning("Failed to close output: " + e.getMessage());
                }
                try {
                    this.socket.close();
                } catch (IOException e) {
                    logger.warning("Failed to close socket: " + e.getMessage());
                }
            }
        }
    }
}
