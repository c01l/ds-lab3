package client;

import chatserver.Chatserver;
import cli.Command;
import cli.Shell;
import util.Config;
import util.LineStreamSplitter;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client implements IClientCli, Runnable {

    private static final String MSG_NOT_LOGGED_IN = "Not logged in.";

    private static final int UDPSIZE = 1024;

    private final Logger logger;

    private String componentName;
    private Config config;
    private InputStream userRequestStream;
    private PrintStream userResponseStream;

    private Shell shell;
    private PrivateMessageReceiver privateMsgReciever;
    private final ExecutorService pool;

    private ConnectionManager connManager;

    private DatagramSocket udpSocket;
    private InetAddress udpServerAddr;
    private int udpServerPort;

    private String lastPublicMsg = null;
    private final Object msgLock = new Object();

    private boolean loggedIn = false;
    private String myName = "";

    /**
     * @param componentName      the name of the component - represented in the prompt
     * @param config             the configuration to use
     * @param userRequestStream  the input stream to read user input from
     * @param userResponseStream the output stream to write the console output to
     */
    public Client(String componentName, Config config,
                  InputStream userRequestStream, PrintStream userResponseStream) {
        this.componentName = componentName;
        this.config = config;
        this.userRequestStream = userRequestStream;
        this.userResponseStream = userResponseStream;

        this.privateMsgReciever = null;

        this.pool = Executors.newFixedThreadPool(4);
        this.logger = Logger.getLogger(componentName);
        this.logger.setLevel(Level.WARNING);
    }

    @Override
    public void run() {

        // start connection to server
        String serverHostname = this.config.getString("chatserver.host");
        int serverPort = this.config.getInt("chatserver.tcp.port");

        this.connManager = new ConnectionManager(serverHostname, serverPort);
        this.connManager.addNewConnectionListener(new NewConnectionListener() {
            @Override
            public void newConnectionEstablished(final ConnectionCapsule capsule) {
                // setup message reciever
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        LineStreamSplitter splitter = capsule.getSplitter();
                        splitter.ensureQueue("!show");
                        for (String line; !Thread.currentThread().isInterrupted() && (line = splitter.readLine("!show")) != null; ) {
                            synchronized (msgLock) {
                                lastPublicMsg = line;
                                userResponseStream.println(line);
                            }
                        }
                        logger.info("Message reciever stopped!");
                    }
                });
            }
        });
        this.connManager.addNewConnectionListener(new NewConnectionListener() {
            @Override
            public void newConnectionEstablished(final ConnectionCapsule capsule) {
                // setup unknown cleaner
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        LineStreamSplitter splitter = capsule.getSplitter();
                        for (String line; !Thread.currentThread().isInterrupted() && (line = splitter.readLine()) != null; ) {
                            logger.warning("Got unknown string!\n'" + line + "'\nDiscarding...");
                        }
                        logger.info("Unknown Cleaner stopped!");
                    }
                });
            }
        });

        try {
            this.udpServerAddr = InetAddress.getByName(serverHostname);
            this.udpServerPort = this.config.getInt("chatserver.udp.port");

            udpSocket = new DatagramSocket();
        } catch (UnknownHostException e) {
            this.logger.log(Level.SEVERE, "Cannot find '" + serverHostname + "'!");
            return;
        } catch (IOException e) {
            this.logger.log(Level.SEVERE, "Cannot open udp port!");
            return;
        }

        // setup shell
        shell = new Shell(this.componentName, this.userRequestStream, this.userResponseStream);
        shell.register(this);
        pool.execute(shell);

    }

    @Command
    @Override
    public String login(String username, String password) throws IOException {
        if (this.loggedIn) {
            return "You are already logged in!";
        }

        if (username.contains(" ") || password.contains(" ")) {
            throw new IllegalArgumentException("Username and password are not allowed to contain whitespaces!");
        }

        final String marker = Chatserver.Marker.MARKER_LOGIN_RESPONSE;

        this.connManager.getConnection().writeLine("!login " + username + " " + password);

        // check splitter
        LineStreamSplitter splitter = this.connManager.getConnection().getSplitter();
        splitter.ensureQueue(marker);

        String response = splitter.readLine(marker);

        logger.info("Login-Response: " + response);

        if (response.equals("Successfully logged in.")) {
            loggedIn = true;
            myName = new String(username);
        }

        return response;
    }

    @Command
    @Override
    public String logout() throws IOException {
        final String marker = Chatserver.Marker.MARKER_LOGOUT_RESPONSE;
        if (loggedIn) {
            // check splitter
            LineStreamSplitter splitter = this.connManager.getConnection().getSplitter();
            splitter.ensureQueue(marker);

            this.connManager.getConnection().writeLine("!logout");

            String response = splitter.readLine(marker);
            if (response == null) {
                logger.info("Read returned null for marker '" + marker + "'");
                return null;
            } else if (response.equals("Successfully logged out")) {
                loggedIn = false;

                this.connManager.closeConnection();
            }
            return response;
        } else {
            return MSG_NOT_LOGGED_IN;
        }
    }

    @Command
    @Override
    public String send(String message) throws IOException {
        if (!loggedIn) {
            return MSG_NOT_LOGGED_IN;
        }

        if ((message = message.trim()).isEmpty())
            return "Empty messages are not sent!";

        final String marker = Chatserver.Marker.MARKER_SEND_RESPONSE;
        LineStreamSplitter splitter = this.connManager.getConnection().getSplitter();
        splitter.ensureQueue(marker);

        this.connManager.getConnection().writeLine("!send " + message);

        String response = splitter.readLine(marker);

        if (response.equals("Sent successfully")) {
            return null;
        }

        return response;
    }

    @Command
    @Override
    public String list() throws IOException {
        // send udp packet to server
        byte[] data = "!list".getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, this.udpServerAddr, this.udpServerPort);
        this.udpSocket.send(packet);

        logger.info("Sent udp packet to " + this.udpServerAddr + ":" + this.udpServerPort);

        StringBuilder responseBuilder = new StringBuilder();

        // wait for response
        byte[] udpResponse = new byte[UDPSIZE];
        DatagramPacket responsePacket = new DatagramPacket(udpResponse, udpResponse.length);
        for (; ; ) {
            logger.info("Waiting for response packet...");

            this.udpSocket.receive(responsePacket);

            logger.info("Got packet");

            String s = new String(udpResponse);
            if (s.endsWith("" + (char) 31)) {
                s = s.replace("" + (char) 31, "");
            }

            responseBuilder.append("- ");
            responseBuilder.append(s);

            if (udpResponse[UDPSIZE - 1] != 31) {
                break; // there will be no next packet
            }
        }
        logger.info("Recieved udp packet(s)");

        // read response from array
        return "Online users:\n" + responseBuilder.toString();
    }

    @Command
    @Override
    public String msg(String username, String message) throws IOException {
        final String MSG_FAILED = "Wrong username or user not reachable.";
        final String MSG_SUCCESS = "%USERNAME% replied with %RESPONSE%";

        if (!loggedIn) {
            return MSG_NOT_LOGGED_IN;
        }

        // find ip of the user
        String lookupResponse = lookup(username);
        // check if response is <IP:PORT>
        String[] split = lookupResponse.split(":");
        if (split.length != 2) {
            logger.warning("Response has the wrong format. Probably exception.");
            return MSG_FAILED;
        }
        InetAddress addr;
        try {
            addr = InetAddress.getByName(split[0]);
        } catch (UnknownHostException e) {
            logger.warning("IPAddress could not be resolved");
            return MSG_FAILED;
        }
        int port;
        try {
            port = Integer.parseInt(split[1]);
            if (!(1 <= port && port <= 65535)) {
                logger.warning("Port out of range (1-65535): " + port);
                return MSG_FAILED;
            }
        } catch (NumberFormatException e) {
            logger.warning("Port is not a number: " + split[1]);
            return MSG_FAILED;
        }

        // open socket for client connection
        ConnectionManager manager = new ConnectionManager(addr.getHostName(), port);
        manager.getConnection().writeLine(this.myName + ": " + message);
        String response = manager.getConnection().readLine();

        manager.shutdown();

        return MSG_SUCCESS.replace("%USERNAME%", username).replace("%RESPONSE%", response);
    }

    @Command
    @Override
    public String lookup(String username) throws IOException {
        if (!loggedIn) {
            return MSG_NOT_LOGGED_IN;
        }

        final String marker = Chatserver.Marker.MARKER_LOOKUP_RESPONSE;

        logger.fine("Start lookup of '" + username + "'");

        LineStreamSplitter splitter = this.connManager.getConnection().getSplitter();
        splitter.ensureQueue(marker);

        this.connManager.getConnection().writeLine("!lookup " + username);
        String response = splitter.readLine(marker);

        logger.info("Lookup request answered with: " + response);

        return response;
    }

    @Command
    @Override
    public String register(String privateAddress) throws IOException {
        if (!loggedIn) {
            return MSG_NOT_LOGGED_IN;
        }

        // validate input
        String[] split = privateAddress.split(":");
        if (split.length != 2) {
            return "Wrong ip format. Expecting <IP:PORT>";
        }

        // source: http://stackoverflow.com/questions/5667371/validate-ipv4-address-in-java
        try {
            InetAddress.getByName(split[0]);
        } catch (UnknownHostException e) {
            return "Wrong ip format";
        }

        int port;
        try {
            port = Integer.parseInt(split[1]);
            if (!(1 <= port && port <= 65535)) {
                return "Port number out of bounds";
            }
            // port in bounds
        } catch (NumberFormatException e) {
            return "Port has to be a number";
        }

        // try to open socket before telling the server its open
        try {
            this.privateMsgReciever = new PrivateMessageReceiver(port, this.userResponseStream);
            pool.execute(this.privateMsgReciever);
        } catch (IOException e) {
            return "Failed to open socket. Did not publish IP + Port to server.";
        }

        final String marker = Chatserver.Marker.MARKER_REGISTER_RESPONSE;

        LineStreamSplitter splitter = this.connManager.getConnection().getSplitter();
        splitter.ensureQueue(marker);

        this.connManager.getConnection().writeLine("!register " + privateAddress);
        String response = splitter.readLine(marker);

        logger.info("Register process has returned: " + response);


        return response;
    }

    @Command
    @Override
    public String lastMsg() throws IOException {
        if (!loggedIn) {
            return MSG_NOT_LOGGED_IN;
        }

        synchronized (this.msgLock) {
            if (this.lastPublicMsg == null) {
                return "No message received!";
            }
            return this.lastPublicMsg;
        }
    }

    @Command
    @Override
    public String exit() throws IOException {
        logger.info("exit called");

        String response = "";
        if (loggedIn) {
            response = logout() + "\n";
        } else {
            logger.info("Do not need to logout!");
        }

        logger.log(Level.FINE, "Closing Shell...");
        this.shell.close();
        logger.log(Level.FINE, "Shell closed");

        if (this.privateMsgReciever != null) {
            logger.fine("Closing private message listener...");
            this.privateMsgReciever.shutdown();
            logger.fine("Private message listener closed!");
        }

        logger.log(Level.FINE, "Shutting down ThreadPool");
        this.pool.shutdown();
        this.connManager.shutdown();
        logger.log(Level.FINE, "ThreadPool shut down!");

        logger.info("Finished exiting");

        return response + "Successfully shutdown client!";
    }

    /**
     * @param args the first argument is the name of the {@link Client} component
     */
    public static void main(String[] args) {
        Client client = new Client(args[0], new Config("client"), System.in,
                System.out);
        client.run();
    }

    // --- Commands needed for Lab 2. Please note that you do not have to
    // implement them for the first submission. ---

    @Override
    public String authenticate(String username) throws IOException {
        // TODO Lab 2
        return null;
    }

}
