package chatserver.stage;

import chatserver.TerminateSessionException;
import chatserver.UserData;
import util.CommunicationChannel;

public interface ClientStage {
    /**
     * Performs a whole stage (eg. login, register) for a client.
     *
     * @param data    the userdata object assigned to the current session
     * @param channel the channel this stage should operate on
     * @return the userdata object returned from this stage
     */
    UserData execute(UserData data, CommunicationChannel channel) throws TerminateSessionException;
}
