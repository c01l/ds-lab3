package client;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by ROLAND on 02.11.2016.
 */
public class ConnectionManager {

    private String hostname;
    private int port;

    private ConnectionCapsule conn;

    private ExecutorService pool;
    private final List<NewConnectionListener> listeners;

    public ConnectionManager(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;

        this.conn = null;

        this.pool = Executors.newFixedThreadPool(1);
        this.listeners = new LinkedList<>();
    }

    /**
     * Retrieves a new connection object to connect to a server
     *
     * @return
     */
    public synchronized ConnectionCapsule getConnection() {
        if (this.conn == null) {
            this.conn = instantiateConnection();

            // inform listeners
            synchronized (this.listeners) {
                for (NewConnectionListener l : this.listeners) {
                    l.newConnectionEstablished(this.conn);
                }
            }
        }

        return this.conn;
    }

    public synchronized void closeConnection() {
        if (this.conn != null) {
            this.conn.close();
            this.conn = null;
        }
    }

    public void shutdown() {
        this.closeConnection();
        this.pool.shutdown();
    }

    private ConnectionCapsule instantiateConnection() {
        try {
            ConnectionCapsule capsule = new ConnectionCapsule(this.hostname, this.port);

            this.pool.execute(capsule.getSplitter());

            return capsule;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void addNewConnectionListener(NewConnectionListener listener) {
        synchronized (this.listeners) {
            this.listeners.add(listener);
        }
    }
}
