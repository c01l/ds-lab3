package chatserver;

import util.CommunicationChannel;

/**
 * Created by ROLAND on 20.10.2016.
 */
public class UserData {
    private CommunicationChannel client;

    private boolean onlineStatus;

    private String name, password;
    private String localAddress;

    public UserData(String name, String password) {
        this.name = name;
        this.setPassword(password);
        this.onlineStatus = false;
        this.client = null;
        this.localAddress = null;
    }

    public CommunicationChannel getClient() {
        return client;
    }

    public void setClient(CommunicationChannel client) {
        this.client = client;
    }

    public boolean isOnline() {
        return onlineStatus;
    }

    public void setOnlineStatus(boolean onlineStatus) {
        this.onlineStatus = onlineStatus;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public void setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
    }
}
