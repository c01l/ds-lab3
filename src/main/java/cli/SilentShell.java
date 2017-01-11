package cli;

import util.LineReader;

import java.io.*;
import java.util.logging.Logger;

/**
 * Created by ROLAND on 28.10.2016.
 */
public class SilentShell extends Shell {

    private final Logger logger;
    private InputStream in;

    public SilentShell(String name, InputStream in, OutputStream out) {
        super(name, in, out);
        this.logger = Logger.getLogger(name);
        this.in = in;
    }

    @Override
    public void run() {
        LineReader lineReader = new LineReader(in);
        logger.info("Using a line reader!");
        try {
            for (String line; !Thread.currentThread().isInterrupted() && (line = lineReader.readLine()) != null; ) {
                Object result;
                try {
                    result = invoke(line);
                } catch (Throwable t) {
                    ByteArrayOutputStream str = new ByteArrayOutputStream(1024);
                    t.printStackTrace(new PrintStream(str, true));
                    result = str.toString();
                }
                if (result != null) {
                    print(result);
                }
            }
        } catch (IOException e) {
            try {
                writeLine("Shell closed");
            } catch (IOException ex) {
                System.out.println(ex.getClass().getName() + ": "
                        + ex.getMessage());
            }
        }
    }

    @Override
    public void writeLine(String line) throws IOException {
        write((line + "\n").getBytes());
    }
}
