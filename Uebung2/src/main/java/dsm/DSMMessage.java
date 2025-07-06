package main.java.dsm;

import org.oxoo2a.sim4da.Message;

/**
 * Nachrichten für DSM-Kommunikation.
 */
public class DSMMessage extends Message {
    private final Type type;
    private final String key;
    private final String value;
    private final long timestamp;
    public DSMMessage(Type type, String key, String value, String senderId) {
        this.type = type;
        this.key = key;
        this.value = value;
        this.timestamp = System.currentTimeMillis();
        this.setSender(senderId);
    }

    public DSMMessage(Type type, String key, String value, String senderId, long timestamp) {
        this.type = type;
        this.key = key;
        this.value = value;
        this.timestamp = timestamp;
        this.setSender(senderId);
    }

    public DSMMessage(Type type, String key, String senderId) {
        this(type, key, null, senderId);
    }

    protected DSMMessage(DSMMessage original) {
        super(original);
        this.type = original.type;
        this.key = original.key;
        this.value = original.value;
        this.timestamp = original.timestamp;
    }

    @Override
    public Message copy() {
        return new DSMMessage(this);
    }

    public Type getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSenderId() {
        return getSender();
    }

    public enum Type {
        WRITE, READ_REQUEST, READ_RESPONSE, SYNC_REQUEST, SYNC_RESPONSE
    }
}
