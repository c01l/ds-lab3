package chatserver;

public class TerminateSessionException extends Exception {
    public TerminateSessionException(String message) {
        super(message);
    }

    public TerminateSessionException(String message, Throwable cause) {
        super(message, cause);
    }

    public TerminateSessionException(Throwable cause) {
        super(cause);
    }
}
