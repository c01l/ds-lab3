package util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface CommunicationChannel {

    /**
     * Returns the {@link InputStream} for this channel.
     *
     * @return
     */
    InputStream getInputStream() throws IOException;

    /**
     * Returns the {@link OutputStream} for this channel.
     *
     * @return
     */
    OutputStream getOutputStream() throws IOException;

    /**
     * Closes the connection
     *
     * @throws IOException
     */
    void close() throws IOException;

}
