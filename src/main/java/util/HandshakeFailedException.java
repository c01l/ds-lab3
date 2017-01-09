package util;

/**
 * This exception should be thrown when a problem during execution of a handshake protocol occurs.
 */
public class HandshakeFailedException extends Exception {
    public HandshakeFailedException(String message) {
        super(message);
    }

    public HandshakeFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public HandshakeFailedException(Throwable cause) {
        super(cause);
    }
}
