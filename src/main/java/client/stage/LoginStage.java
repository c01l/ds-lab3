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
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.Key;
import java.util.logging.Logger;


public class LoginStage implements Stage {
    private static final Logger logger = Logger.getLogger("LoginStage");

    private InputStream userInputStream;
    private OutputStream userOutputStream;

    private String host;
    private int port;

    private Key serverKey;

    private StageGenerator generator;
    private Shell shell;
    private CommunicationChannel loggedInChannel = null;
    private String loggedInUser = null;

    public LoginStage(StageGenerator generator, InputStream userInputStream, OutputStream userOutputStream, String host, int port, Key serverKey) {
        this.userInputStream = userInputStream;
        this.userOutputStream = userOutputStream;
        this.host = host;
        this.port = port;
        this.serverKey = serverKey;
        this.generator = generator;
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

        // now that the shell closed itself we can read the Connection that we are going to use

        if(loggedInChannel != null) {
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
        public String authenticate(String username) { // TODO change name
            Key clientKey;
            try {
                // load private key
                clientKey = Keys.readPrivatePEM(new File("keys/client/" + username + ".pem"));
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
    }
}
