package chatserver.stage;

import chatserver.ServerHandshakePerformer;
import chatserver.TerminateSessionException;
import chatserver.UserData;
import util.CommunicationChannel;
import util.HandshakeFailedException;
import util.HandshakePerformer;

import java.security.Key;
import java.util.List;

public class LoginStage implements ClientStage {

    private Key serverPrivateKey;
    private List<UserData> userDB;

    public LoginStage(Key serverPrivateKey, List<UserData> userDB) {
        this.serverPrivateKey = serverPrivateKey;
        this.userDB = userDB;
    }

    @Override
    public UserData execute(UserData data, CommunicationChannel channel) throws TerminateSessionException {

        if (data != null) {
            throw new AssertionError("The user should never be logged in in this stage!");
        }

        ServerHandshakePerformer handshake = new ServerHandshakePerformer(this.serverPrivateKey, this.userDB);

        try {
            CommunicationChannel secureChannel = handshake.execute(channel);
            UserData u = handshake.getLastLoggedIn();

            u.setClient(secureChannel);
            u.setOnlineStatus(true);

            return u;
        } catch (HandshakeFailedException e) {
            throw new TerminateSessionException(e);
        }
    }
}
