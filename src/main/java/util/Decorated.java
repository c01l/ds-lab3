package util;

/**
 * Created by ROLAND on 29.12.2016.
 */
public interface Decorated<T> {
    /**
     * Returns the real object behind the current decorated one.
     *
     * @return
     */
    T getReal();
}
