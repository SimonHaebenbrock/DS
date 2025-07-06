package main.java.dsm;

import org.oxoo2a.sim4da.Message;
import org.oxoo2a.sim4da.UnknownNodeException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * AP-Variante: Verfügbarkeit + Partitionstoleranz.
 * Simuliert bewusst Inkonsistenzen durch Nachrichtenverluste.
 */
public class APDSM extends AbstractDSM {

    private final Map<String, Long> timestampMap;
    private final Random random = new Random();

    private static final double MESSAGE_DROP_RATE = 0.7;
    private static final double MESSAGE_DELAY_RATE = 0.8;
    private static final double VALUE_MANIPULATION_RATE = 0.4;
    private static final long MAX_DELAY_MS = 5000;

    private final List<DelayedMessage> delayedMessages = new CopyOnWriteArrayList<>();
    private final Set<String> partitionedNodes = new HashSet<>();
    private final Map<String, String> staleValues = new ConcurrentHashMap<>();
    private Thread messageProcessingThread;

    public APDSM(String nodeName) {
        super(nodeName);
        this.timestampMap = new ConcurrentHashMap<>();

        startDelayedMessageProcessor();
        simulateRandomPartitions();
    }

    private void startDelayedMessageProcessor() {
        messageProcessingThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    processDelayedMessages();
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        messageProcessingThread.setName("APDSM-MessageProcessor-" + NodeName());
        messageProcessingThread.setDaemon(true);
        messageProcessingThread.start();
    }

    private void processDelayedMessages() {
        long currentTime = System.currentTimeMillis();
        List<DelayedMessage> messagesToProcess = new ArrayList<>();

        for (DelayedMessage msg : delayedMessages) {
            if (currentTime >= msg.deliveryTime) {
                messagesToProcess.add(msg);
            }
        }

        // Nachrichten in zufälliger Reihenfolge verarbeiten
        if (messagesToProcess.size() > 1) {
            for (int i = messagesToProcess.size() - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                DelayedMessage temp = messagesToProcess.get(i);
                messagesToProcess.set(i, messagesToProcess.get(j));
                messagesToProcess.set(j, temp);
            }
        }

        for (DelayedMessage msg : messagesToProcess) {
            processMessage(msg);
            delayedMessages.remove(msg);
        }
    }

    private void processMessage(DelayedMessage delayedMsg) {
        // Gelegentlich Nachrichten manipulieren
        if (delayedMsg.message.getType() == DSMMessage.Type.WRITE &&
                random.nextDouble() < VALUE_MANIPULATION_RATE) {

            DSMMessage original = delayedMsg.message;
            DSMMessage manipulated = new DSMMessage(
                    original.getType(),
                    original.getKey(),
                    manipulateValue(original.getValue()),
                    original.getSenderId()
            );
            deliverMessage(manipulated, delayedMsg.receiver);
        } else {
            deliverMessage(delayedMsg.message, delayedMsg.receiver);
        }
    }

    private void deliverMessage(DSMMessage message, String receiver) {
        if (receiver.equals(NodeName())) {
            receive(message);
        } else {
            try {
                send(message, receiver);
            } catch (UnknownNodeException e) {
                logger.warning("Knoten nicht erreichbar: " + receiver);
            }
        }
    }

    private void simulateRandomPartitions() {
        if (random.nextDouble() < 0.4) {
            new Thread(() -> {
                try {
                    Thread.sleep(300 + random.nextInt(500));
                    simulateNetworkPartition();
                    Thread.sleep(800 + random.nextInt(800));
                    resolveNetworkPartition();

                    if (random.nextDouble() < 0.6) {
                        Thread.sleep(1000 + random.nextInt(800));
                        simulateNetworkPartition();
                        Thread.sleep(500 + random.nextInt(800));
                        resolveNetworkPartition();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    private void simulateNetworkPartition() {
        partitionedNodes.clear();
        for (String nodeId : knownNodes) {
            if (random.nextDouble() < 0.4) {
                partitionedNodes.add(nodeId);
            }
        }
        logger.warning("Netzwerkpartition: " + partitionedNodes.size() + " Knoten nicht erreichbar");
    }

    private void resolveNetworkPartition() {
        int partitionSize = partitionedNodes.size();
        partitionedNodes.clear();
        logger.warning("Netzwerkpartition aufgehoben: " + partitionSize + " Knoten wieder erreichbar");
    }

    private String manipulateValue(String original) {
        if (original == null || original.isEmpty()) {
            return "0";
        }

        try {
            int value = Integer.parseInt(original);
            if (random.nextBoolean()) {
                value += random.nextInt(3) + 1;
            } else {
                value -= random.nextInt(2) + 1;
                if (value < 0) value = 0;
            }
            return String.valueOf(value);
        } catch (NumberFormatException e) {
            return original;
        }
    }

    @Override
    public void write(String key, String value) {
        String oldValue = localStore.get(key);
        if (oldValue != null && !oldValue.equals(value)) {
            staleValues.put(key, oldValue);
        }

        long timestamp = System.currentTimeMillis();
        localStore.put(key, value);
        timestampMap.put(key, timestamp);

        if (random.nextDouble() < 0.2) {
            try {
                Thread.sleep(100 + random.nextInt(200));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        DSMMessage writeMessage = new DSMMessage(DSMMessage.Type.WRITE, key, value, NodeName(), timestamp);
        broadcastAsyncMessage(writeMessage);
    }

    private void broadcastAsyncMessage(DSMMessage message) {
        for (String nodeId : knownNodes) {
            if (partitionedNodes.contains(nodeId) || random.nextDouble() < MESSAGE_DROP_RATE) {
                continue;
            }

            if (random.nextDouble() < MESSAGE_DELAY_RATE) {
                long delay = 50 + random.nextInt((int) MAX_DELAY_MS);
                delayedMessages.add(new DelayedMessage(message, nodeId, System.currentTimeMillis() + delay));
            } else {
                try {
                    send(message, nodeId);
                } catch (UnknownNodeException e) {
                    logger.warning("Knoten nicht erreichbar: " + nodeId);
                }
            }
        }
    }

    @Override
    public String read(String key) {
        // Manchmal veraltete Werte zurückgeben
        if (random.nextDouble() < 0.3) {
            String staleValue = staleValues.get(key);
            if (staleValue != null) {
                return staleValue;
            }
        }

        // Manchmal "verlorene" Werte simulieren
        if (random.nextDouble() < 0.15) {
            return "";
        }

        return localStore.getOrDefault(key, "");
    }

    @Override
    public void receive(Message message) {
        if (!(message instanceof DSMMessage)) {
            return;
        }

        DSMMessage dsmMessage = (DSMMessage) message;

        if (random.nextDouble() < 0.3) {
            try {
                Thread.sleep(random.nextInt(50));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (dsmMessage.getType() == DSMMessage.Type.WRITE) {
            processWriteMessage(dsmMessage);
        }
    }

    private void processWriteMessage(DSMMessage message) {
        String key = message.getKey();
        String value = message.getValue();
        long receivedTimestamp = message.getTimestamp();

        Long currentTimestamp = timestampMap.get(key);
        if (currentTimestamp == null || receivedTimestamp > currentTimestamp) {
            localStore.put(key, value);
            timestampMap.put(key, receivedTimestamp);
        }
    }

    private static class DelayedMessage {
        final DSMMessage message;
        final String receiver;
        final long deliveryTime;

        DelayedMessage(DSMMessage message, String receiver, long deliveryTime) {
            this.message = message;
            this.receiver = receiver;
            this.deliveryTime = deliveryTime;
        }
    }
}
