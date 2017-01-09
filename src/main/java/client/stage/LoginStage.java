package client.stage;

import cli.Command;
import cli.Shell;
import cli.SilentShell;
import client.ClientHandshakePerformer;
import util.CommunicationChannel;
import util.HandshakeFailedException;
import util.Keys;
import util.SimpleSocketCommunicationChannel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.security.Key;
import java.util.logging.Level;
import java.util.logging.Logger;


public class LoginStage implements Stage {
    private static final Logger logger = Logger.getLogger("LoginStage");

    private static final int UDPSIZE = 1024;

    private InputStream userInputStream;
    private OutputStream userOutputStream;

    private String host;
    private int port;

    private Key serverKey;
    private String clientKeyDir;

    private StageGenerator generator;
    private Shell shell;
    private CommunicationChannel loggedInChannel = null;
    private String loggedInUser = null;

    private boolean exitFlag = false;

    private InetAddress udpServerAddr;
    private int udpServerPort;

    public LoginStage(StageGenerator generator, InputStream userInputStream, OutputStream userOutputStream, String host, int port, Key serverKey, String clientKeyDir, String hostname, int udpPort) {
        this.userInputStream = userInputStream;
        this.userOutputStream = userOutputStream;
        this.host = host;
        this.port = port;
        this.serverKey = serverKey;
        this.generator = generator;
        this.clientKeyDir = clientKeyDir;

        try {
            this.udpServerAddr = InetAddress.getByName(hostname);
            this.udpServerPort = udpPort;
        } catch (UnknownHostException e) {
            logger.log(Level.SEVERE, "Cannot find '" + hostname + "'!");
        }
    }

    @Override
    public Stage execute() {
        LoginShell shellCmds = new LoginShell();
        this.shell = new SilentShell("", this.userInputStream, this.userOutputStream);
        this.shell.register(shellCmds);

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                shell.run();
            }
        });
        t.start();
        while (!t.isInterrupted() && t.isAlive()) {
            try {
                t.join();
                logger.info("Join returned!");
                logger.info("Interrupted: " + t.isInterrupted());
                logger.info("Alive: " + t.isAlive());
            } catch (InterruptedException e) {
                // do nothing
            }
        }

        logger.info("Shell is closed!");

        if (this.exitFlag) {
            return null; // close program
        }

        // now that the shell closed itself we can read the Connection that we are going to use

        if (loggedInChannel != null) {
            return generator.generatePerformingStage(loggedInChannel, loggedInUser);
        } else {
            try {
                this.shell.writeLine("Login failed");
            } catch (IOException e) {
                logger.warning("Could not tell user that the login failed");
            }
            return this;
        }
    }

    private class LoginShell {
        @Command
        public String authenticate(String username) {
            Key clientKey;
            try {
                // load private key
                clientKey = Keys.readPrivatePEM(new File(clientKeyDir + "/" + username + ".pem"));
            } catch (IOException e) {
                logger.warning("failed to retrieve key for client");
                return "Failed to read private key!";
            }
            try {
                // Open socket and start handshake
                Socket socket = new Socket(host, port);

                SimpleSocketCommunicationChannel channel = new SimpleSocketCommunicationChannel(socket);

                ClientHandshakePerformer handshakePerformer = new ClientHandshakePerformer(username, clientKey, serverKey);
                loggedInChannel = handshakePerformer.execute(channel);
                loggedInUser = username;

                Thread.currentThread().interrupt();

                return "Logged in successfully!";
            } catch (UnknownHostException e) {
                logger.warning("Chatserver-Name cannot be resolved! " + e.getMessage());
            } catch (IOException e) {
                logger.warning(e.getMessage());
                e.printStackTrace();
            } catch (HandshakeFailedException e) {
                logger.warning("Failed to perform handshake!");
                e.printStackTrace();
            }
            return "Handshake failed!";
        }

        @Command
        public String exit() {
            exitFlag = true;
            Thread.currentThread().interrupt();
            return "Exiting...";
        }

        @Command
        public String logout() {
            return "Not logged in!";
        }

        @Command
        public String send(String msg) {
            return "Not logged in!";
        }

        @Command
        public String msg(String name, String msg) {
            return "Not logged in!";
        }

        @Command
        public String lookup(String name) {
            return "Not logged in!";
        }

        @Command
        public String register(String ip) {
            return "Not logged in!";
        }

        @Command
        public String lastMsg() {
            return "Not logged in!";
        }

        @Command
        public String list() throws IOException {
            DatagramSocket udpSocket = new DatagramSocket();

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
    }
}
