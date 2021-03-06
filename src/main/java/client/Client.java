package client;

import client.stage.Stage;
import client.stage.StageGenerator;
import util.Config;
import util.Keys;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.security.Key;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class Client implements Runnable {

    private final Logger logger;

    private String componentName;
    private Config config;
    private InputStream userRequestStream;
    private PrintStream userResponseStream;

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

        this.logger = Logger.getLogger(componentName);
    }

    @Override
    public void run() {
        // start connection to server
        String serverHostname = this.config.getString("chatserver.host");
        int serverPort = this.config.getInt("chatserver.tcp.port");

        Key serverKey;
        try {
            serverKey = Keys.readPublicPEM(new File(this.config.getString("chatserver.key")));
        } catch (IOException e) {
            logger.warning("Failed to load server public key!");
            return;
        }

        int serverUdpPort = this.config.getInt("chatserver.udp.port");
        String clientKeyDir = this.config.getString("keys.dir");

        String hmacPath = this.config.getString("hmac.key");
        logger.info("Shared Secret Dir: " + hmacPath);

        StageGenerator generator = new StageGenerator(this.userRequestStream, this.userResponseStream, serverHostname, serverPort, serverUdpPort, serverKey, clientKeyDir, hmacPath);

        Stage stage = generator.generateLoginStage();
        while (stage != null) {
            logger.info("Starting stage: " + stage.toString());
            stage = stage.execute();
            logger.info("Stage finished");
        }

        logger.info("Client closed!");
    }

    /**
     * @param args the first argument is the name of the {@link Client} component
     */
    public static void main(String[] args) {
        LogManager.getLogManager().reset();
        Client client = new Client(args[0], new Config("client"), System.in,
                System.out);
        client.run();
    }
}
