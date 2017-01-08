package client.stage;

import chatserver.Chatserver;
import cli.Command;
import cli.SilentShell;
import client.ConnectionCapsule;
import client.ConnectionManager;
import client.IClientCli;
import client.PrivateMessageReceiver;
import org.bouncycastle.util.encoders.Base64;
import util.CommunicationChannel;
import util.HMAC;
import util.Keys;
import util.LineStreamSplitter;

import javax.crypto.Mac;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PerformingStage implements Stage {
    private static final Logger logger = Logger.getLogger("PerformingStage");

    private StageGenerator generator;
    private CommunicationChannel channel;
    private InputStream userRequestStream;
    private OutputStream userResponseStream;

    private ClientShell shell;
    private ExecutorService pool;

    private ConnectionCapsule capsule;
    private PrivateMessageReceiver privateMsgReciever;

    private String lastPublicMsg = null;
    private final Object msgLock = new Object();

    private static final int UDPSIZE = 1024;
    private DatagramSocket udpSocket;
    private InetAddress udpServerAddr;
    private int udpServerPort;

    private String myName;
    private boolean logoutFlag = false;

    private Key sharedSecret;

    public PerformingStage(StageGenerator generator, CommunicationChannel channel, InputStream userRequestStream, OutputStream userResponseStream, String hostname, int udpPort, String myName, String hmacPath) {
        this.generator = generator;
        this.channel = channel;
        this.userRequestStream = userRequestStream;
        this.userResponseStream = userResponseStream;
        this.myName = myName;

        try {
            this.udpServerAddr = InetAddress.getByName(hostname);
            this.udpServerPort = udpPort;
        } catch (UnknownHostException e) {
            logger.log(Level.SEVERE, "Cannot find '" + hostname + "'!");
        }

        try {
            this.sharedSecret = Keys.readSecretKey(new File(hmacPath));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to load shared secret!");
        }

        this.pool = Executors.newFixedThreadPool(4);
    }

    @Override
    public Stage execute() {
        logger.info("Entered Performing Stage");

        try {
            this.capsule = new ConnectionCapsule(this.channel);
            pool.execute(this.capsule.getSplitter());

            pool.execute(new Runnable() {
                @Override
                public void run() {
                    LineStreamSplitter splitter = capsule.getSplitter();
                    splitter.ensureQueue("!show");
                    for (String line; !Thread.currentThread().isInterrupted() && (line = splitter.readLine("!show")) != null; ) {
                        synchronized (msgLock) {
                            lastPublicMsg = line;
                            println(line);
                        }
                    }
                    logger.info("Message reciever stopped!");
                }
            });

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

            udpSocket = new DatagramSocket();


            shell = new ClientShell("ClientShell", this.userRequestStream, this.userResponseStream);
            shell.run(); // Wait for the shell to terminate

        } catch (IOException e) {
            logger.warning(e.getMessage());
        }

        logger.info("Exited Performing Stage");

        // in case we just want a logout -> open new login-stage
        if (this.logoutFlag) {
            return generator.generateLoginStage();
        }

        return null; // end program
    }

    private void closeStage() {
        logger.log(Level.FINE, "Closing server connection");
        this.capsule.close();
        logger.log(Level.FINE, "Server connection closed!");

        logger.log(Level.FINE, "Closing Shell...");
        shell.close();
        logger.log(Level.FINE, "Shell closed!");

        if (privateMsgReciever != null) {
            logger.fine("Closing private message listener...");
            privateMsgReciever.shutdown();
            logger.fine("Private message listener closed!");
        }

        logger.log(Level.FINE, "Shutting down ThreadPool");
        this.pool.shutdown();
        logger.log(Level.FINE, "ThreadPool shut down!");

        logger.info("Finished exiting");
    }

    private void println(String msg) {
        try {
            this.userResponseStream.write((msg + "\n").getBytes());
            this.userResponseStream.flush();
        } catch (IOException e) {
            // not possible to print to user stream so at least log it
            logger.warning("Couldnot tell user: " + msg);
        }
    }

    private class ClientShell extends SilentShell {
        public ClientShell(String name, InputStream in, OutputStream out) {
            super(name, in, out);

            this.register(this);
        }

        @Command
        @Deprecated
        public String login(String username, String password) throws IOException {
            return "This command should not be used!";
        }

        @Command
        public String logout() throws IOException {
            final String marker = Chatserver.Marker.MARKER_LOGOUT_RESPONSE;

            // check splitter
            LineStreamSplitter splitter = capsule.getSplitter();
            splitter.ensureQueue(marker);

            capsule.writeLine("!logout");

            String response = splitter.readLine(marker);
            if (response == null) {
                // server already terminated the connection before telling us if it was successful
                // there is no connection left, so jsut logout anyways
                logger.info("Read returned null for marker '" + marker + "'");
            } else if (!response.equals("Successfully logged out")) {
                return response;
            }

            logoutFlag = true;
            closeStage();

            return response;
        }

        @Command
        public String send(String message) throws IOException {
            if ((message = message.trim()).isEmpty())
                return "Empty messages are not sent!";

            final String marker = Chatserver.Marker.MARKER_SEND_RESPONSE;
            LineStreamSplitter splitter = capsule.getSplitter();
            splitter.ensureQueue(marker);

            capsule.writeLine("!send " + message);

            String response = splitter.readLine(marker);

            if (response.equals("Sent successfully")) {
                return null;
            }

            return response;
        }

        @Command
        public String list() throws IOException {
            // send udp packet to server
            byte[] data = "!list".getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, udpServerAddr, udpServerPort);
            udpSocket.send(packet);

            logger.info("Sent udp packet to " + udpServerAddr + ":" + udpServerPort);

            StringBuilder responseBuilder = new StringBuilder();

            // wait for response
            byte[] udpResponse = new byte[UDPSIZE];
            DatagramPacket responsePacket = new DatagramPacket(udpResponse, udpResponse.length);
            for (; ; ) {
                logger.info("Waiting for response packet...");

                udpSocket.receive(responsePacket);

                logger.info("Got packet");

                String s = new String(udpResponse);
                if (s.endsWith("" + (char) 31)) {
                    s = s.replace("" + (char) 31, "");
                }

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
        public String msg(String username, String message) throws IOException {
            final String MSG_FAILED = "Wrong username or user not reachable.";
            final String MSG_SUCCESS = "%USERNAME% replied with %RESPONSE%";

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

            String finalMessage = myName + ": " + message;
            String hmac = new String(HMAC.generateHMAC(finalMessage, sharedSecret));

            // open socket for client connection
            ConnectionManager manager = new ConnectionManager(addr.getHostName(), port);
            manager.getConnection().writeLine(hmac + " " + finalMessage);

            logger.info("Sending Private Message: " + hmac + " " + finalMessage);
            String response = manager.getConnection().readLine();
            manager.shutdown();

            String responseHMACString = response.substring(0, response.indexOf(' '));
            String responseMessageString = response.substring(response.indexOf(' ') + 1);

            byte[] generatedResponseHMAC = HMAC.generateHMAC(responseMessageString, sharedSecret);

            if (!MessageDigest.isEqual(generatedResponseHMAC, responseHMACString.getBytes())) {
                System.out.println("Received Message was tampered!");
                logger.info("Received Message was tampered!");
                return "Received Message was tampered!";
            } else {
                if (responseMessageString.startsWith("!tampered")) {
                    System.out.println("Your message was tampered!");
                    logger.info("Your message was tampered!");
                    return "Your message was tampered!";
                }
            }

            return MSG_SUCCESS.replace("%USERNAME%", username).replace("%RESPONSE%", response);
        }

        @Command
        public String lookup(String username) throws IOException {
            final String marker = Chatserver.Marker.MARKER_LOOKUP_RESPONSE;

            logger.fine("Start lookup of '" + username + "'");

            LineStreamSplitter splitter = capsule.getSplitter();
            splitter.ensureQueue(marker);

            capsule.writeLine("!lookup " + username);
            String response = splitter.readLine(marker);

            logger.info("Lookup request answered with: " + response);

            return response;
        }

        @Command
        public String register(String privateAddress) throws IOException {
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
                privateMsgReciever = new PrivateMessageReceiver(port, new PrintStream(userResponseStream), sharedSecret);
                pool.execute(privateMsgReciever);
            } catch (IOException e) {
                return "Failed to open socket. Did not publish IP + Port to server.";
            }

            final String marker = Chatserver.Marker.MARKER_REGISTER_RESPONSE;

            LineStreamSplitter splitter = capsule.getSplitter();
            splitter.ensureQueue(marker);

            capsule.writeLine("!register " + privateAddress);
            String response = splitter.readLine(marker);

            logger.info("Register process has returned: " + response);


            return response;
        }

        @Command
        public String lastMsg() throws IOException {
            synchronized (msgLock) {
                if (lastPublicMsg == null) {
                    return "No message received!";
                }
                return lastPublicMsg;
            }
        }

        @Command
        public String exit() throws IOException {
            logger.info("exit called");

            closeStage();

            return "Successfully shutdown client!";
        }
    }
}
