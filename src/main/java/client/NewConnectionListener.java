package client;

public interface NewConnectionListener {

    /**
     * Gets called in case a new connection is established.
     * @param capsule the new {@link ConnectionCapsule}
     */
    void newConnectionEstablished(ConnectionCapsule capsule);

}
