package util;

/**
 * This exception is thrown in case a transmitted message is broken.
 */
public class BrokenMessageException extends Exception {
    public BrokenMessageException(String message) {
        super(message);
    }

    public BrokenMessageException(Throwable cause) {
        super(cause);
    }

    public BrokenMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
