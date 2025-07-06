package main.java.app;

import main.java.dsm.DSMLogger;
import main.java.dsm.DistributedSharedMemory;
import org.oxoo2a.sim4da.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Counter-App für CAP-Theorem Demo.
 * Jeder Knoten hat seinen eigenen Zähler.
 */
public class CounterApp extends Node {

    private final DistributedSharedMemory dsm;
    private final String nodeId;
    private final Random random;
    private final Logger logger;
    private final String[] allNodeIds;
    private final Map<String, Integer> lastKnownValues;
    private final int totalIterations;
    private final String dsmType;

    private int currentIteration = 0;
    private int readOperations = 0;
    private int writeOperations = 0;
    private int detectedInconsistencies = 0;
    private boolean initialized = false;
    private volatile boolean applicationRunning = false;
    private Thread simulationThread = null;

    public CounterApp(String nodeName, DistributedSharedMemory dsm, String[] allNodeIds, int totalIterations, String dsmType) {
        super(nodeName);
        this.dsm = dsm;
        this.nodeId = nodeName;
        this.random = new Random();
        this.logger = Logger.getLogger(this.getClass().getName() + "-" + nodeName);
        this.allNodeIds = allNodeIds;
        this.lastKnownValues = new HashMap<>();
        this.totalIterations = totalIterations;
        this.dsmType = dsmType;

        for (String id : allNodeIds) {
            lastKnownValues.put(id, 0);
        }

        logger.warning("CounterApp " + nodeName + " erstellt (" + dsmType + ")");
        initializeCounter();
    }

    private void initializeCounter() {
        try {
            logger.warning("Initialisiere Zähler für " + nodeId);
            String counterKey = getCounterKey(nodeId);
            dsm.write(counterKey, "0");
            writeOperations++;
            logger.warning("Zähler initialisiert: " + counterKey + " = 0");
            initialized = true;
        } catch (Exception e) {
            logger.severe("Fehler beim Initialisieren: " + e.getMessage());
        }
    }

    @Override
    protected void engage() {
        logger.warning("CounterApp auf " + nodeId + " gestartet");
        if (!initialized) {
            initializeCounter();
        }
        logger.warning("CounterApp auf " + nodeId + " bereit");
    }

    public void step() {
        if (!initialized) {
            initializeCounter();
            if (!initialized) {
                logger.warning("CounterApp auf " + nodeId + " konnte nicht initialisiert werden");
                return;
            }
        }

        currentIteration++;
        logger.info("Iteration " + currentIteration + " von " + totalIterations);

        if (currentIteration > totalIterations) {
            return;
        }

        try {
            if (random.nextBoolean()) {
                incrementOwnCounter();
            }
            checkAllCounters();
        } catch (Exception e) {
            logger.severe("Fehler in step(): " + e.getMessage());
        }
    }

    private void incrementOwnCounter() {
        try {
            String counterKey = getCounterKey(nodeId);
            String currentValueStr = dsm.read(counterKey);
            readOperations++;

            int currentValue = parseValue(currentValueStr);
            int newValue = currentValue + 1;

            dsm.write(counterKey, String.valueOf(newValue));
            writeOperations++;
            lastKnownValues.put(nodeId, newValue);

            logger.info("Zähler inkrementiert: " + counterKey + " = " + newValue);
        } catch (Exception e) {
            logger.severe("Fehler beim Inkrementieren: " + e.getMessage());
        }
    }

    private void checkAllCounters() {
        Map<String, Integer> currentValues = new HashMap<>();
        int maxValue = 0;

        for (String id : allNodeIds) {
            String counterKey = getCounterKey(id);
            try {
                String valueStr = dsm.read(counterKey);
                readOperations++;

                if (valueStr != null && !valueStr.isEmpty()) {
                    int currentValue = parseValue(valueStr);
                    currentValues.put(id, currentValue);
                    maxValue = Math.max(maxValue, currentValue);
                }
            } catch (Exception e) {
                logger.warning("Fehler beim Lesen von " + counterKey + ": " + e.getMessage());
            }
        }

        for (String id : allNodeIds) {
            if (!currentValues.containsKey(id)) continue;

            int lastValue = lastKnownValues.getOrDefault(id, 0);
            int currentValue = currentValues.get(id);

            checkForInconsistencies(id, lastValue, currentValue, maxValue);
            lastKnownValues.put(id, currentValue);
        }
    }

    private void checkForInconsistencies(String id, int lastValue, int currentValue, int maxValue) {
        String counterKey = getCounterKey(id);

        // Rücksprünge
        if (currentValue < lastValue) {
            detectedInconsistencies++;
            logInconsistency("Zähler " + counterKey + " ist zurückgesprungen von " + lastValue + " auf " + currentValue);
        }
        // Unerwartete Sprünge
        else if (id.equals(nodeId) && currentValue > lastValue + 1) {
            detectedInconsistencies++;
            logInconsistency("Eigener Zähler " + counterKey + " hat unerwarteten Sprung von " + lastValue + " auf " + currentValue);
        }
        // Divergenzen bei AP
        else if (dsmType.equals("AP") && currentValue < maxValue - 2) {
            detectedInconsistencies++;
            logInconsistency("Divergenz - Zähler " + counterKey + " hat Wert " + currentValue + ", Maximum ist " + maxValue);
        }
        // Inkonsistenz bei CP/CA
        else if ((dsmType.equals("CP") || dsmType.equals("CA")) && currentValue != maxValue && maxValue > 0) {
            detectedInconsistencies++;
            logInconsistency("Konsistenz verletzt - Zähler " + counterKey + " hat Wert " + currentValue + ", sollte " + maxValue + " sein");
        }
    }

    private void logInconsistency(String message) {
        String fullMessage = "[" + dsmType + "] INKONSISTENZ ERKANNT: " + message;
        logger.severe(fullMessage);
        DSMLogger.logResult(fullMessage);
    }

    private int parseValue(String valueStr) {
        if (valueStr == null || valueStr.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(valueStr);
        } catch (NumberFormatException e) {
            logger.warning("Ungültiger Wert: " + valueStr);
            return 0;
        }
    }

    private String getCounterKey(String nodeId) {
        return "counter_" + nodeId;
    }

    public void startApplication() {
        if (applicationRunning) {
            logger.warning("Anwendung auf " + nodeId + " läuft bereits");
            return;
        }

        logger.warning("Starte Anwendung auf " + nodeId);
        applicationRunning = true;

        stopExistingThread();

        simulationThread = new Thread(() -> {
            try {
                for (int i = 0; i < totalIterations; i++) {
                    if (Thread.currentThread().isInterrupted() || !applicationRunning) {
                        logger.warning("Simulation auf " + nodeId + " unterbrochen");
                        break;
                    }

                    step();
                    currentIteration = i + 1;

                    try {
                        Thread.sleep(50 + random.nextInt(150));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warning("Simulation auf " + nodeId + " unterbrochen");
                        break;
                    }
                }

                logger.warning("Simulation auf " + nodeId + " abgeschlossen");
                applicationRunning = false;
            } catch (Exception e) {
                logger.severe("Fehler während Simulation auf " + nodeId + ": " + e.getMessage());
                applicationRunning = false;
            }
        });

        simulationThread.setName("Simulation-" + nodeId);
        simulationThread.start();
    }

    public void stopApplication() {
        logger.warning("Stoppe Anwendung auf " + nodeId);
        applicationRunning = false;
        stopExistingThread();
    }

    private void stopExistingThread() {
        if (simulationThread != null && simulationThread.isAlive()) {
            simulationThread.interrupt();
            try {
                simulationThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void initializeOnly() {
        logger.warning("Initialisiere CounterApp auf " + nodeId + " (ohne Thread)");
        if (!initialized) {
            initializeCounter();
        }
        logger.warning("CounterApp auf " + nodeId + " bereit für Start");
    }

    // Getter
    public String getNodeId() {
        return nodeId;
    }

    public int getCurrentIteration() {
        return currentIteration;
    }

    public int getTotalIterations() {
        return totalIterations;
    }

    public int getDetectedInconsistencies() {
        return detectedInconsistencies;
    }

    public int getReadOperations() {
        return readOperations;
    }

    public int getWriteOperations() {
        return writeOperations;
    }
}
