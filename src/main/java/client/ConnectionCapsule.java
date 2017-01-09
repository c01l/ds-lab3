package client;

import util.CommunicationChannel;
import util.LineReader;
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

    private CommunicationChannel channel;
    private LineReader in;
    private PrintWriter out;
    private LineStreamSplitter splitter;

    public ConnectionCapsule(CommunicationChannel channel) throws IOException {
        this.channel = channel;
        this.in = new LineReader(this.channel.getInputStream());
        this.out = new PrintWriter(this.channel.getOutputStream());
        this.splitter = new LineStreamSplitter(this.in);
    }

    public LineStreamSplitter getSplitter() {
        return this.splitter;
    }

    public LineReader getIn() {
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
            LOGGER.fine("Closing output...");
            this.channel.close();
            LOGGER.fine("Output closed!");
        }catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to close socket!");
            e.printStackTrace();
        }
    }
}
