package chatserver;

import cli.Command;
import cli.SilentShell;

import java.io.IOException;
import java.io.OutputStream;
import java.rmi.RemoteException;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import chatserver.Chatserver.Marker;
import nameserver.INameserverForChatserver;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import util.CommunicationChannel;

public class ChatserverClientHandler extends SilentShell implements IServerClientHandler {

    private static final Logger LOGGER = Logger.getLogger("CharserverClientHandler");
    static { LOGGER.setLevel(Level.WARNING); }

    private static final String MSG_RESPONSE_LOGIN_SUCCESSFUL = "Successfully logged in.";
    private static final String MSG_RESPONSE_LOGIN_FAILED = "Wrong username or password!";
    private static final String MSG_RESPONSE_LOGIN_ALREADYLOGGEDIN = "You are already logged in!";

    private static final String MSG_RESPONSE_LOGOUT_SUCCESSFUL = "Successfully logged out";
    private static final String MSG_RESPONSE_LOGOUT_FAILED = "Logout failed!";

    private static final String MSG_RESPONSE_SEND_NOTLOGGEDIN = "You need to be logged in to send messages!";
    private static final String MSG_RESPONSE_SEND_SUCCESSFUL = "Sent successfully";

    private static final String MSG_RESPONSE_REGISTER_SUCCESSFUL = "Successfully registered address for %USERNAME%.";
    private static final String MSG_RESPONSE_REGISTER_USERNOTFOUND = "User not found!";

    private static final String MSG_RESPONSE_LOOKUP_FAILED = "Wrong username or user not registered.";

    private static final String MSG_UNKNOWN_COMMAND = "Unknown command!";
    private static final String MSG_RESPONSE_NOTLOGGEDIN = "Not logged in.";

    private CommunicationChannel channel;
    private INameserverForChatserver nameserver;
    private final List<UserData> userDB;
    private final UserData user; // Thats the currently logged in one

    public ChatserverClientHandler(String name, CommunicationChannel channel, UserData user, List<UserData> userDB, INameserverForChatserver nameserver) throws IOException {
        super(name, channel.getInputStream(), channel.getOutputStream());
        this.nameserver = nameserver;
        this.channel = channel;
        this.user = user;
        this.userDB = userDB;

        this.register(this);
    }


    @Command("!login")
    @Override
    @Deprecated
    public String login(String username, String password) {
        System.out.println("Client did something funny");
        throw new UnsupportedOperationException("Login is replaced by authenticate");
        /*
        // search for user in the user database
        for (UserData d : userDB) {
            if (d.getName().equals(username)) {
                // found user -> check password
                if (d.getPassword().equals(password)) {
                    // password correct
                    synchronized (userDB) {
                        if (d.isOnline()) {
                            LOGGER.warning("User '" + username + "' already logged in!");
                            return Chatserver.Marker.MARKER_LOGIN_RESPONSE + MSG_RESPONSE_LOGIN_ALREADYLOGGEDIN;
                        }

                        d.setOnlineStatus(true);
                        d.setClient(this.channel);
                    }
                    LOGGER.info("User '" + username + "' logged in!");
                    return Chatserver.Marker.MARKER_LOGIN_RESPONSE + MSG_RESPONSE_LOGIN_SUCCESSFUL;
                } else {
                    // password incorrect
                    LOGGER.info("Wrong login for '" + username + "': " + password);
                    return Chatserver.Marker.MARKER_LOGIN_RESPONSE + MSG_RESPONSE_LOGIN_FAILED;
                }
            }
        }
        LOGGER.info("User '" + username + "' not found!");
        return Chatserver.Marker.MARKER_LOGIN_RESPONSE + MSG_RESPONSE_LOGIN_FAILED;
        */
    }


    @Command("!logout")
    @Override
    public String logout() {
        if (!this.user.isOnline()) {
            return Marker.MARKER_LOGOUT_RESPONSE + MSG_RESPONSE_NOTLOGGEDIN;
        }

        synchronized (this.user) {
            LOGGER.info("Logout from " + this.user.getName());

            this.user.setOnlineStatus(false);
            this.user.setClient(null);

            try {
                this.writeLine(Chatserver.Marker.MARKER_LOGOUT_RESPONSE + MSG_RESPONSE_LOGOUT_SUCCESSFUL);
            } catch (IOException e) {
                // could not send logout message / was successful anyways
                LOGGER.warning("Failed to sent logout message. Was successful anyways. Continue with life...");
            }

            this.close();
        }

        return null;
    }

    @Command("!send")
    @Override
    public String send(String message) {
        message = this.user.getName() + ": " + message; // append sender

        // send to other clients
        synchronized (this.userDB) {
            for (UserData d : this.userDB) {
                if (d != this.user && d.getClient() != null) {
                    synchronized (d) {
                        try {
                            OutputStream os = d.getClient().getOutputStream();
                            os.write(("!show" + message + "\n").getBytes());
                            os.flush();
                        } catch (IOException e) {
                            LOGGER.warning("Failed to send message to " + d.getName() + " (channel: " + d.getClient() + ")");
                        }
                    }
                }
            }
        }

        return Chatserver.Marker.MARKER_SEND_RESPONSE + MSG_RESPONSE_SEND_SUCCESSFUL;
    }

    @Command("!register")
    @Override
    public String register(String ipPort) {
        UserData d = findUserData(this.channel);
        if (d == null || !d.isOnline()) {
            return Chatserver.Marker.MARKER_REGISTER_RESPONSE + MSG_RESPONSE_NOTLOGGEDIN;
        }

        try {
	    this.user.setLocalAddress(ipPort);

            this.nameserver.registerUser(d.getName(), ipPort);
            LOGGER.info("User set local ip to " + ipPort);
            return Chatserver.Marker.MARKER_REGISTER_RESPONSE + MSG_RESPONSE_REGISTER_SUCCESSFUL.replace("%USERNAME%", d.getName());
        } catch (RemoteException e) {
            // TODO: proper error handling
            e.printStackTrace();
        } catch (AlreadyRegisteredException e) {
            // TODO: proper error handling
            e.printStackTrace();
        } catch (InvalidDomainException e) {
            // TODO: proper error handling
            e.printStackTrace();
        }
        return null;
    }

    @Command("!lookup")
    @Override
    public String lookup(String username) {
        UserData own = findUserData(this.channel);
        if(own==null || !own.isOnline()) {
            return Marker.MARKER_LOOKUP_RESPONSE + MSG_RESPONSE_NOTLOGGEDIN;
        }

        int firstDot = username.indexOf(".");
        String name = username.substring(0, firstDot);
        String nameserver = username.substring(firstDot + 1);
        try {
            String localAddr = this.nameserver.getNameserver(nameserver).lookup(name);
            if(localAddr != null) {
                return Marker.MARKER_LOOKUP_RESPONSE + localAddr;
            } else {
                // TODO
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return Marker.MARKER_LOOKUP_RESPONSE + MSG_RESPONSE_LOOKUP_FAILED;
    }

    @Override
    public void close() {
        super.close();

        try {
            this.channel.close();
        } catch (IOException e) {
            LOGGER.warning("Failed to close channel!");
            e.printStackTrace();
        }
    }
}
