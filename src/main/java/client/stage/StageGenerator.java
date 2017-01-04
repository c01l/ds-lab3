package client.stage;

import util.CommunicationChannel;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;

/**
 * Created by ROLAND on 03.01.2017.
 */
public class StageGenerator {
    private InputStream userInputStream;
    private OutputStream userOutputStream;

    private String host;
    private int tcpPort;
    private int udpPort;

    private Key serverKey;

    public StageGenerator(InputStream userInputStream, OutputStream userOutputStream, String host, int tcpPort, int udpPort, Key serverKey) {
        this.userInputStream = userInputStream;
        this.userOutputStream = userOutputStream;
        this.host = host;
        this.tcpPort = tcpPort;
        this.serverKey = serverKey;
        this.udpPort = udpPort;
    }

    public LoginStage generateLoginStage() {
        return new LoginStage(this, this.userInputStream, this.userOutputStream, this.host, this.tcpPort, this.serverKey);
    }

    public PerformingStage generatePerformingStage(CommunicationChannel channel, String username) {
        return new PerformingStage(this, channel, this.userInputStream, this.userOutputStream, this.host, this.udpPort, username);
    }

}
