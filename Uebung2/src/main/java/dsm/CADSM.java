package main.java.dsm;

import org.oxoo2a.sim4da.Message;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CA-Variante: Konsistenz + Verfügbarkeit.
 * Versagt bei Netzwerkpartitionen.
 */
public class CADSM extends AbstractDSM {

    private static final long TIMEOUT_MS = 1000;
    private static final int MAX_RETRIES = 3;
    private static final int MIN_DELAY = 5;
    private static final int MAX_DELAY = 20;
    private static final double PARTITION_THRESHOLD = 0.6;
    private static final double NODE_FAILURE_RATE = 0.05;
    private final Random random = new Random();
    private final Set<String> pendingAcknowledgments;
    private final Map<String, Set<String>> acknowledgedNodes = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> responseReceivedNodes = new ConcurrentHashMap<>();
    private volatile boolean partitionDetected = false;

    public CADSM(String nodeName) {
        super(nodeName);
        this.pendingAcknowledgments = ConcurrentHashMap.newKeySet();

        if (random.nextDouble() < 0.2) {
            new Thread(() -> {
                try {
                    Thread.sleep(500 + random.nextInt(1000));
                    simulatePartition();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    private void simulatePartition() {
        if (random.nextDouble() < 0.5) {
            logger.warning("CA-DSM: Netzwerkpartition erkannt!");
            partitionDetected = true;

            if (random.nextDouble() < 0.7) {
                new Thread(() -> {
                    try {
                        Thread.sleep(1000 + random.nextInt(2000));
                        resolvePartition();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }
        }
    }

    private void resolvePartition() {
        logger.warning("CA-DSM: Netzwerkverbindungen wiederhergestellt");
        partitionDetected = false;
    }

    @Override
    public void write(String key, String value) {
        if (partitionDetected) {
            logger.warning("CA-DSM: Schreiboperation abgelehnt wegen Partition: " + key);
            return;
        }

        if (knownNodes.isEmpty()) {
            localStore.put(key, value);
            return;
        }

        if (random.nextDouble() < 0.7) {
            try {
                Thread.sleep(MIN_DELAY + random.nextInt(MAX_DELAY - MIN_DELAY));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        String requestId = UUID.randomUUID().toString();
        pendingAcknowledgments.add(requestId);

        Set<String> acknowledgedForRequest = ConcurrentHashMap.newKeySet();
        acknowledgedForRequest.add(NodeName());
        acknowledgedNodes.put(requestId, acknowledgedForRequest);

        localStore.put(key, value);

        DSMMessage writeMessage = new DSMMessage(DSMMessage.Type.WRITE, key + ":" + requestId, value, NodeName());
        broadcastMessage(writeMessage);

        long startTime = System.currentTimeMillis();
        int retries = 0;

        while (acknowledgedNodes.get(requestId).size() < knownNodes.size() + 1 &&
                System.currentTimeMillis() - startTime < TIMEOUT_MS &&
                retries < MAX_RETRIES &&
                !partitionDetected) {

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (System.currentTimeMillis() - startTime >= TIMEOUT_MS) {
                retries++;
                startTime = System.currentTimeMillis();

                if (retries < MAX_RETRIES) {
                    logger.warning("Timeout - Wiederhole Versuch " + retries + "/" + MAX_RETRIES);
                    broadcastMessage(writeMessage);
                }
            }
        }

        if (!partitionDetected && acknowledgedNodes.containsKey(requestId)) {
            int missingAcks = knownNodes.size() + 1 - acknowledgedNodes.get(requestId).size();
            double missingRatio = (double) missingAcks / (knownNodes.size() + 1);

            if (missingRatio >= PARTITION_THRESHOLD) {
                logger.warning("CA-DSM: Partition erkannt! " + String.format("%.1f", missingRatio * 100) + "% nicht erreichbar");
                simulatePartition();
            }
        }

        acknowledgedNodes.remove(requestId);
        pendingAcknowledgments.remove(requestId);

        if (!partitionDetected && retries >= MAX_RETRIES) {
            logger.severe("Schreiboperation gescheitert: " + key);
        }
    }

    @Override
    public String read(String key) {
        if (partitionDetected) {
            logger.warning("CA-DSM: Leseoperation abgelehnt wegen Partition: " + key);
            return null;
        }

        if (!knownNodes.isEmpty() && random.nextDouble() < 0.8) {
            try {
                synchronizeValue(key);
            } catch (Exception e) {
                logger.warning("Fehler bei Synchronisierung: " + e.getMessage());
                if (random.nextDouble() < 0.3) {
                    simulatePartition();
                    return null;
                }
            }
        }

        if (!partitionDetected && random.nextDouble() < 0.9) {
            try {
                Thread.sleep(MIN_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return localStore.getOrDefault(key, "");
    }

    private void synchronizeValue(String key) {
        String requestId = UUID.randomUUID().toString();
        Set<String> responseNodes = ConcurrentHashMap.newKeySet();
        responseNodes.add(NodeName());
        responseReceivedNodes.put(requestId, responseNodes);

        DSMMessage syncRequest = new DSMMessage(DSMMessage.Type.SYNC_REQUEST, key + ":" + requestId, NodeName());
        broadcastMessage(syncRequest);

        long startTime = System.currentTimeMillis();
        while (responseNodes.size() < knownNodes.size() + 1 &&
                System.currentTimeMillis() - startTime < TIMEOUT_MS / 2 &&
                !partitionDetected) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        responseReceivedNodes.remove(requestId);
    }

    @Override
    public void receive(Message message) {
        if (partitionDetected && random.nextDouble() < 0.7) {
            return;
        }

        if (random.nextDouble() < NODE_FAILURE_RATE) {
            return;
        }

        if (!(message instanceof DSMMessage dsmMessage)) {
            return;
        }

        if (!partitionDetected && random.nextDouble() < 0.8) {
            try {
                Thread.sleep(MIN_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        switch (dsmMessage.getType()) {
            case WRITE:
                handleWriteMessage(dsmMessage);
                break;
            case SYNC_REQUEST:
                handleSyncRequestMessage(dsmMessage);
                break;
            case SYNC_RESPONSE:
                handleSyncResponseMessage(dsmMessage);
                break;
        }
    }

    private void handleWriteMessage(DSMMessage message) {
        String[] keyParts = message.getKey().split(":");
        if (keyParts.length != 2) {
            return;
        }

        String key = keyParts[0];
        String requestId = keyParts[1];
        String value = message.getValue();
        String senderId = message.getSenderId();

        localStore.put(key, value);

        DSMMessage ackMessage = new DSMMessage(DSMMessage.Type.WRITE, key + ":" + requestId, "ACK", NodeName());
        sendMessage(ackMessage, senderId);
    }

    private void handleSyncRequestMessage(DSMMessage message) {
        String[] keyParts = message.getKey().split(":");
        if (keyParts.length != 2) {
            return;
        }

        String key = keyParts[0];
        String requestId = keyParts[1];
        String senderId = message.getSenderId();

        String value = localStore.getOrDefault(key, "");

        DSMMessage syncResponse = new DSMMessage(DSMMessage.Type.SYNC_RESPONSE,
                key + ":" + requestId,
                value,
                NodeName());
        sendMessage(syncResponse, senderId);
    }

    private void handleSyncResponseMessage(DSMMessage message) {
        String[] keyParts = message.getKey().split(":");
        if (keyParts.length != 2) {
            return;
        }

        String key = keyParts[0];
        String requestId = keyParts[1];
        String senderId = message.getSenderId();
        String value = message.getValue();

        if (value != null && !value.isEmpty()) {
            String localValue = localStore.getOrDefault(key, "");
            if (localValue.isEmpty() && !value.isEmpty()) {
                localStore.put(key, value);
            }
        }

        Set<String> responseNodes = responseReceivedNodes.get(requestId);
        if (responseNodes != null) {
            responseNodes.add(senderId);
        }
    }
}
