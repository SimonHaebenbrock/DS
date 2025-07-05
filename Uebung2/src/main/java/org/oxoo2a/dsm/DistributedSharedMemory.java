package main.java.org.oxoo2a.dsm;

/**
 * Einfaches Key-Value-Interface für die drei CAP-Varianten.
 */
public interface DistributedSharedMemory {
    void write(String key, String value);
    String read(String key);
}
