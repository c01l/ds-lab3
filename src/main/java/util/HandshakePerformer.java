package util;

public interface HandshakePerformer {

    /**
     * Performs a handshake protocol on a given connection and returns a {@link CommunicationChannel} after the handshare.
     * In case the handshake fails an error will rise.
     *
     * @param start the CommunicationChannel you are currently on.
     * @return a decorated CommunicationChannel
     */
    CommunicationChannel execute(CommunicationChannel start) throws HandshakeFailedException;
}
