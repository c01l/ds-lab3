package chatserver;

/**
 * Created by ROLAND on 28.10.2016.
 */
public interface IServerClientHandler {
    /**
     * Tries to find a user in the user database by the given username and then checks if the password matches.
     * In case the login proceedure ist successful, the user gets set as online and the socket gets set too.
     *
     * @param username the username you want to log in as
     * @param password the password of the given user
     * @return a response string
     */
    String login(String username, String password);

    /**
     * Logs the currently logged in user out. In case no user is logged in the response string will say that.
     *
     * @return a response string or null if nothing should be sent
     */
    String logout();

    /**
     * Sends a message to all online users in the user database.
     * The message will not be sent to the sending client.
     *
     * @param message the message you want to send
     */
    String send(String message);

    /**
     * This method allows a client to register a local ip port combination to the server, that the server
     * can forward to clients that want to open a private connection to that client.
     *
     * @param ipPort the compination of ip and port. Syntax "IP:PORT"
     * @return a response string
     */
    String register(String ipPort);

    /**
     * Fetches the registered (IP, port)-combination for the given user.
     * If the user is offline or has no registered entry then an empty string will be returned.
     *
     * @param username the username of the user you are looking for
     * @return the IP/port combination in the format "IP:PORT" or an empty string
     */
    String lookup(String username);
}
