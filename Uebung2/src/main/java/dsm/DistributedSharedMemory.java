package main.java.dsm;

/**
 * Einfaches Key-Value-Interface für die drei CAP-Varianten.
 */
public interface DistributedSharedMemory {
    void write(String key, String value);
    String read(String key);
}
