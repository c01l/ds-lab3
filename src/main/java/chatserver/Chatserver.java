package chatserver;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.security.Key;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import chatserver.stage.LoginStage;
import chatserver.stage.PerformingStage;
import cli.Command;
import cli.Shell;
import nameserver.INameserverForChatserver;
import util.CommunicationChannel;
import util.Config;
import util.Keys;
import util.SimpleSocketCommunicationChannel;

public class Chatserver implements IChatserverCli, Runnable {

    private static final int UDPSIZE = 1024;
    private static final String MSG_UDP_UNKNOWN_COMMAND = "Unknown command";

    private final Logger logger;

    private String componentName;
    private Config config;
    private InputStream userRequestStream;
    private PrintStream userResponseStream;

    private ArrayList<UserData> userData;

    private UDPServerThread udpServer;
    private AsynchronousTCPServer tcpServer;
    private Shell shell;

    private Key serverPrivateKey;
    private String clientKeyDir;


    private String registryHost;
    private int registryPort;
    private String rootId;
    private INameserverForChatserver nameserver;

    /**
     * @param componentName      the name of the component - represented in the prompt
     * @param config             the configuration to use
     * @param userRequestStream  the input stream to read user input from
     * @param userResponseStream the output stream to write the console output to
     */
    public Chatserver(String componentName, Config config,
                      InputStream userRequestStream, PrintStream userResponseStream) {
        this.componentName = componentName;
        this.config = config;
        this.userRequestStream = userRequestStream;
        this.userResponseStream = userResponseStream;

        logger = Logger.getLogger(this.componentName);
        //logger.setLevel(Level.WARNING);

        this.registryPort = this.config.getInt("registry.port");
        this.registryHost = this.config.getString("registry.host");
        this.rootId = this.config.getString("root_id");

        this.userData = new ArrayList<>();
        fillUserData(this.userData, new Config("user"));
        Collections.sort(this.userData, new Comparator<UserData>() {
            @Override
            public int compare(UserData o1, UserData o2) {
                if (o1 == null) {
                    return -1;
                } else if (o2 == null) {
                    return 1;
                }
                return o1.getName().compareTo(o2.getName());
            }
        });
    }

    private void fillUserData(List<UserData> list, Config config) {
        for (String key : config.listKeys()) {
            logger.info("Looking at: " + key);

            int loc = key.indexOf(".password");
            if (loc >= 0) {
                // its a user
                String name = key.substring(0, loc);
                String password = config.getString(key);


                list.add(new UserData(name, password, null));
                logger.info("Successfully added user '" + name + "'!");
            }
        }
    }

    @Override
    public void run() {
        // load server key
        try {
            this.serverPrivateKey = Keys.readPrivatePEM(new File(this.config.getString("key")));
        } catch (IOException e) {
            logger.warning("Failed to load server private key!");
            e.printStackTrace();
        }

        this.clientKeyDir = this.config.getString("keys.dir");


        try {
            this.nameserver = (INameserverForChatserver) LocateRegistry.getRegistry(this.registryHost, this.registryPort).lookup(this.rootId);
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (NotBoundException e) {
            e.printStackTrace();
        }

        // setup TCP Server (requests are handled by ChatserverClientHandlerFactory)
        tcpServer = new AsynchronousTCPServer(this.config.getInt("tcp.port"), new ChatserverClientHandlerFactory());
        tcpServer.start();

        // setup UDP Server
        udpServer = new UDPServerThread(this.config.getInt("udp.port"));
        udpServer.start();

        // start main loop to handle user input
        shell = new Shell(componentName, this.userRequestStream, this.userResponseStream);
        shell.register(this);
        shell.run();
    }

    @Override
    @Command("!users")
    public String users() throws IOException {

        StringBuilder builder = new StringBuilder();

        synchronized (this.userData) {
            for (UserData data : this.userData) {
                builder.append(data.getName());
                builder.append("\t");
                builder.append(data.isOnline() ? "online" : "offline");
                builder.append("\n");
            }
        }

        return builder.toString();
    }

    @Override
    @Command("!exit")
    public String exit() throws IOException {
        // logout users
        synchronized (this.userData) {
            for (UserData data : this.userData) {
                CommunicationChannel s = data.getClient();
                if (s != null) {
                    s.close();
                }
            }
        }
        // shutdown TCP server
        this.tcpServer.interrupt();

        // shutdown UDP server
        this.udpServer.interrupt();

        // close the shell
        this.shell.close();

        return "Successfully shutdown server.";
    }

    private class ChatserverClientHandlerFactory implements ClientHandlerFactory {
        @Override
        public Runnable createClientHandler(Socket client) throws IOException {
            final CommunicationChannel channel = new SimpleSocketCommunicationChannel(client);
            return new Runnable() {
                @Override
                public void run() {
                    UserData d;
                    try {
                        LoginStage loginStage = new LoginStage(serverPrivateKey, userData, clientKeyDir);
                        d = loginStage.execute(null, channel);
                    } catch (TerminateSessionException e) {
                        logger.warning("Exception occured while logging in, terminating session!");
                        try {
                            channel.close();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        return;
                    }

                    logger.info("Successfully logged in user: " + d.getName());

                    try {
                        PerformingStage performingStage = new PerformingStage(userData, nameserver);
                        d = performingStage.execute(d, d.getClient());
                    } catch (TerminateSessionException e) {
                        logger.warning("Exception occured while performing, terminating session!");
                        try {
                            channel.close();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                        return;
                    }

                    // client shell exited -> closing connection
                    try {
                        channel.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
        }
    }

    /**
     * @param args the first argument is the name of the {@link Chatserver}
     *             component
     */
    public static void main(String[] args) {
        Chatserver chatserver = new Chatserver(args[0],
                new Config("chatserver"), System.in, System.out);
        chatserver.run();
    }

    private class UDPServerThread extends Thread {
        private final Logger LOGGER = Logger.getLogger("UDPServerThread");

        private DatagramSocket socket;

        private int port;

        public UDPServerThread(int port) {
            this.port = port;

            this.LOGGER.setLevel(Level.WARNING);
        }

        @Override
        public synchronized void start() {
            try {
                this.socket = new DatagramSocket(this.port);

                logger.info("Opened UDP Socket on " + this.port);

                super.start();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to open socket", e);
            }
        }

        @Override
        public void interrupt() {
            this.socket.close();

            super.interrupt();
        }

        @Override
        public void run() {
            byte[] recvBuffer = new byte[256];
            DatagramPacket recvPacket = new DatagramPacket(recvBuffer, recvBuffer.length);

            while (!this.isInterrupted()) {
                try {

                    logger.info("Waiting for udp packet...");

                    this.socket.receive(recvPacket);

                    logger.info("Got udp packet!");

                    String cmd = new String(recvBuffer);
                    cmd = cmd.substring(0, cmd.indexOf("\0"));
                    if (cmd.equals("!list")) {
                        LOGGER.info("List command called");

                        // compile response
                        StringBuilder builder = new StringBuilder();
                        synchronized (userData) {
                            for (UserData d : userData) {
                                if (d.isOnline()) {
                                    builder.append(d.getName());
                                    builder.append("\n");
                                }
                            }
                        }

                        byte[] toSend = builder.toString().getBytes();

                        // sending as many packets as needed, because of buffer size
                        byte[] buffer = new byte[UDPSIZE];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, recvPacket.getSocketAddress());
                        for (int pos = 0; pos < builder.length(); pos += (UDPSIZE - 1)) {
                            System.arraycopy(toSend, pos, buffer, 0, Math.min((UDPSIZE - 1), toSend.length - pos));

                            // add another packet will follow flag to the package if needed
                            if (pos + UDPSIZE - 1 < builder.length()) {
                                buffer[UDPSIZE - 1] = 31; // Unit Seperator
                            }

                            this.socket.send(packet);
                        }

                    } else {
                        LOGGER.warning("Unknown command: '" + cmd + "'");

                        byte[] buffer = MSG_UDP_UNKNOWN_COMMAND.getBytes();
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, recvPacket.getSocketAddress());
                        this.socket.send(packet);
                    }


                } catch (IOException e) {
                    if (this.socket.isClosed()) {
                        // Thats why...
                        break;
                    } else {
                        LOGGER.log(Level.SEVERE, "Failed to receive packet", e);
                        e.printStackTrace();
                    }
                }
            }

            LOGGER.info("UDP Server stopped. Closing...");

            this.interrupt();
        }
    }

    public static class Marker {
        public static final String MARKER_LOGIN_RESPONSE = "!loginResponse";
        public static final String MARKER_LOGOUT_RESPONSE = "!logoutResponse";
        public static final String MARKER_SEND_RESPONSE = "!sendResponse";
        public static final String MARKER_REGISTER_RESPONSE = "!registerResponse";
        public static final String MARKER_LOOKUP_RESPONSE = "!lookupResponse";
    }

}
