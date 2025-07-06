package main.java.dsm;

import org.oxoo2a.sim4da.Message;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CP-Variante: Konsistenz + Partitionstoleranz.
 * Verwendet Quorum für Operationen.
 */
public class CPDSM extends AbstractDSM {

    private final Map<String, CountDownLatch> pendingReads;
    private final Map<String, String> readResponses;
    private final Map<String, Integer> writeAcknowledgments;
    private final Random random = new Random();

    private static final long TIMEOUT_MS = 200;
    private static final double QUORUM_FACTOR = 0.3;
    private static final double CONSISTENCY_CHECK_RATE = 0.05;

    public CPDSM(String nodeName) {
        super(nodeName);
        this.pendingReads = new ConcurrentHashMap<>();
        this.readResponses = new ConcurrentHashMap<>();
        this.writeAcknowledgments = new ConcurrentHashMap<>();
    }

    private int calculateQuorumSize() {
        int totalNodes = knownNodes.size() + 1;
        return Math.max((int) (totalNodes * QUORUM_FACTOR), (totalNodes / 2) + 1);
    }

    @Override
    public void write(String key, String value) {
        if (knownNodes.isEmpty()) {
            localStore.put(key, value);
            return;
        }

        if (random.nextDouble() < CONSISTENCY_CHECK_RATE) {
            try {
                Thread.sleep(100 + random.nextInt(150));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        int quorumSize = calculateQuorumSize();
        String requestId = UUID.randomUUID().toString();
        String requestKey = key + "_" + requestId;

        writeAcknowledgments.put(requestKey, 1);
        localStore.put(key, value);

        DSMMessage writeMessage = new DSMMessage(DSMMessage.Type.WRITE, key + ":" + requestId, value, NodeName());
        broadcastMessage(writeMessage);

        long startTime = System.currentTimeMillis();
        while (writeAcknowledgments.get(requestKey) < quorumSize &&
                System.currentTimeMillis() - startTime < TIMEOUT_MS) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        if (writeAcknowledgments.get(requestKey) >= quorumSize) {
            logger.info("Write-Quorum erreicht für " + key);
        } else {
            logger.warning("Write-Quorum nicht erreicht für " + key);
            if (random.nextDouble() < 0.25) {
                localStore.remove(key);
            }
        }

        writeAcknowledgments.remove(requestKey);
    }

    @Override
    public String read(String key) {
        if (knownNodes.isEmpty()) {
            return localStore.getOrDefault(key, "");
        }

        if (random.nextDouble() < CONSISTENCY_CHECK_RATE) {
            try {
                Thread.sleep(50 + random.nextInt(100));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        int quorumSize = calculateQuorumSize();
        String requestId = UUID.randomUUID().toString();
        String requestKey = key + "_" + requestId;

        CountDownLatch latch = new CountDownLatch(quorumSize - 1);
        pendingReads.put(requestKey, latch);

        String localValue = localStore.getOrDefault(key, "");
        readResponses.put(requestKey, localValue);

        DSMMessage readMessage = new DSMMessage(DSMMessage.Type.READ_REQUEST, key + ":" + requestId, NodeName());
        broadcastMessage(readMessage);

        boolean quorumReached = false;
        try {
            quorumReached = latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanup(requestKey);
            return localValue;
        }

        if (!quorumReached) {
            logger.warning("Read-Quorum nicht erreicht für " + key);
            if (random.nextDouble() < 0.3) {
                cleanup(requestKey);
                return null;
            }
        }

        String result = readResponses.get(requestKey);
        cleanup(requestKey);
        return result;
    }

    private void cleanup(String requestKey) {
        pendingReads.remove(requestKey);
        readResponses.remove(requestKey);
    }

    @Override
    public void receive(Message message) {
        if (!(message instanceof DSMMessage)) {
            return;
        }

        DSMMessage dsmMessage = (DSMMessage) message;

        if (random.nextDouble() < 0.1) {
            try {
                Thread.sleep(100 + random.nextInt(150));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        switch (dsmMessage.getType()) {
            case WRITE:
                handleWriteMessage(dsmMessage);
                break;
            case READ_REQUEST:
                handleReadRequestMessage(dsmMessage);
                break;
            case READ_RESPONSE:
                handleReadResponseMessage(dsmMessage);
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

    private void handleReadRequestMessage(DSMMessage message) {
        String[] keyParts = message.getKey().split(":");
        if (keyParts.length != 2) {
            return;
        }

        String key = keyParts[0];
        String requestId = keyParts[1];
        String senderId = message.getSenderId();

        String value = localStore.getOrDefault(key, "");

        DSMMessage responseMessage = new DSMMessage(DSMMessage.Type.READ_RESPONSE, key + ":" + requestId, value, NodeName());
        sendMessage(responseMessage, senderId);
    }

    private void handleReadResponseMessage(DSMMessage message) {
        String[] keyParts = message.getKey().split(":");
        if (keyParts.length != 2) {
            return;
        }

        String key = keyParts[0];
        String requestId = keyParts[1];
        String requestKey = key + "_" + requestId;
        String value = message.getValue();

        CountDownLatch latch = pendingReads.get(requestKey);
        if (latch == null) {
            return;
        }

        readResponses.put(requestKey, value);
        latch.countDown();
    }
}
