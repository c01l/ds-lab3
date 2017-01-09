package util;

import java.io.IOException;
import java.io.InputStream;

/**
 * A {@link LineReader} reads from an {@link InputStream} until a new line ('\n') character is reached.
 */
public class LineReader {
    private InputStream source;

    public LineReader(InputStream source) {
        this.source = source;
    }

    public String readLine() throws IOException{
        StringBuilder builder = new StringBuilder();

        for(;;){
            int c = this.source.read();
		    if(c == 10 || c == 0) {
                return builder.toString();
            } else if(c == -1) {
                if(builder.length() == 0) {
                    return null;
                } else {
                    return builder.toString();
                }
            }

            builder.append((char) c);
        }
    }

}
