package Cache.CacheLoader;

public class CacheLoadException extends RuntimeException {
    public CacheLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}