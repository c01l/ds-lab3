package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ROLAND on 29.10.2016.
 */
public class LineStreamSplitter implements Runnable {

    private BufferedReader in;
    private boolean stopped = false;

    /*
     * Storse a prefix with its read strings
     */
    private final ConcurrentHashMap<String, Queue<String>> storage;

    public LineStreamSplitter(BufferedReader in) {
        this.in = in;
        this.storage = new ConcurrentHashMap<>();

        this.registerPrefix("");
    }

    public void registerPrefix(String prefix) {
        synchronized (this.storage) {
            // check if there is a list already containing that prefix
            for (String key : this.storage.keySet()) {
                if (prefix.startsWith(key) && !key.isEmpty()) {
                    throw new IllegalArgumentException("You cannot register the prefix '" + prefix +
                            "' as there is already '" + key + "' in the list");
                }
            }

            // ready to register
            this.storage.put(prefix, new LinkedList<String>());
        }
    }

    public void storeLine(String line) {
        synchronized (this.storage) {
            List<String> keys = new ArrayList<>(this.storage.keySet());
            Collections.sort(keys, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    return o2.length() - o1.length();
                }
            });

            // System.out.println(Arrays.toString(keys.toArray()));

            for (String prefix : keys) {
                if (line.startsWith(prefix)) {
                    Queue<String> lines = this.storage.get(prefix);
                    synchronized (lines) {
                        // System.out.println(prefix + " got the line!");
                        lines.add(line);
                        lines.notify();
                        break;
                    }
                }
            }
        }
    }

    /**
     * read from a {@link Queue} for a specific prefix
     *
     * @param prefix       the prefix you are looking for
     * @param removePrefix tells if you want to remove the prefix from the string
     * @return a line or null if nothing will be read again
     */
    public String readLine(String prefix, boolean removePrefix) {
        for (Queue<String> lines; !Thread.currentThread().isInterrupted() && !stopped; ) {
            lines = this.storage.get(prefix);
            if(lines == null) {
                throw new IllegalArgumentException("Prefix queue does not exist!");
            }

            synchronized (lines) {
                if (lines.size() > 0) {
                    String l = lines.poll();
                    if (removePrefix) {
                        l = l.substring(prefix.length());
                    }
                    return l;
                }
                try {
                    lines.wait();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        return null;
    }

    public String readLine(String prefix) {
        return this.readLine(prefix, true);
    }

    /**
     * Read from the default {@link Queue}
     *
     * @return a line from the default {@link Queue} or null if there wont be coming anything else
     */
    public String readLine() {
        return this.readLine("", false);
    }

    @Override
    public void run() {
        try {
            for (String line; (line = this.in.readLine()) != null; ) {
                this.storeLine(line);
            }
        } catch (IOException e) {
            // do nothing, just quit
        }

        // inform all readers that we are done
        this.stopped = true;
        for(Queue<String> l : this.storage.values()) {
            synchronized (l) {
                l.notifyAll();
            }
        }
    }

    public List<String> getPrefixes() {
        List<String> ret;
        synchronized (this.storage) {
            ret = new ArrayList<>(this.storage.keySet());
        }
        ret.remove("");
        return ret;
    }

    public void ensureQueue(String prefix) {
        if(!this.storage.containsKey(prefix)) {
            this.registerPrefix(prefix);
        }
    }
}
