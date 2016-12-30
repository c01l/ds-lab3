package chatserver.stage;

import chatserver.ChatserverClientHandler;
import chatserver.TerminateSessionException;
import chatserver.UserData;
import util.CommunicationChannel;

import java.io.IOException;
import java.util.List;

/**
 * Created by ROLAND on 30.12.2016.
 */
public class PerformingStage implements ClientStage {

    private List<UserData> userDataList;

    public PerformingStage(List<UserData> userDataList) {
        this.userDataList = userDataList;
    }

    @Override
    public UserData execute(UserData data, CommunicationChannel channel) throws TerminateSessionException {
        try {
            ChatserverClientHandler clientHandler = new ChatserverClientHandler("", data.getClient(), data, this.userDataList);
            clientHandler.run();

            return data;
        } catch (IOException e) {
            throw new TerminateSessionException(e);
        }
    }
}
