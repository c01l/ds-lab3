package client;

import util.CommunicationChannel;
import util.LineStreamSplitter;
import util.SimpleSocketCommunicationChannel;

import java.io.*;
import java.net.Socket;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class stores all open streams for a remote connection.
 */
public class ConnectionCapsule implements Closeable {
    private static final Logger LOGGER = Logger.getLogger("ConnectionCapsule");
    static {
        LOGGER.setLevel(Level.WARNING);
    }

    private Socket socket;
    private CommunicationChannel channel;
    private BufferedReader in;
    private PrintWriter out;
    private LineStreamSplitter splitter;

    public ConnectionCapsule(String hostname, int port) throws IOException {
        this.socket = new Socket(hostname, port);
        this.channel = new SimpleSocketCommunicationChannel(this.socket);
        this.in = new BufferedReader(new InputStreamReader(this.channel.getInputStream()));
        this.out = new PrintWriter(this.channel.getOutputStream());
        this.splitter = new LineStreamSplitter(this.in);
    }

    public LineStreamSplitter getSplitter() {
        return this.splitter;
    }

    public BufferedReader getIn() {
        return this.in;
    }

    public PrintWriter getOut() {
        return this.out;
    }

    public void writeLine(String line) {
        this.getOut().println(line);
        this.getOut().flush();
    }

    public String readLine() throws IOException {
        return this.getIn().readLine();
    }

    @Override
    public void close() {
        try {
            LOGGER.fine("Closing input...");
            this.socket.shutdownInput();
            LOGGER.fine("Input closed!");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to close incoming connection");
            e.printStackTrace();
        }

        try {
            LOGGER.fine("Closing output...");
            this.channel.close();
            LOGGER.fine("Output closed!");
        }catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to close socket!");
            e.printStackTrace();
        }

        try {
            LOGGER.fine("Closing socket...");
            this.socket.close();
            LOGGER.fine("Socket closed!");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to close socket");
            e.printStackTrace();
        }
    }
}
