package main.java.dsm;

import main.java.app.CounterApp;
import org.oxoo2a.sim4da.Simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CAP-Theorem Simulation mit 3 DSM-Varianten.
 * Konfiguration: 5 Knoten, 5 Iterationen.
 */
public class DSMSimulation {

    private static final Logger logger = Logger.getLogger(DSMSimulation.class.getName());
    private static final int NODE_COUNT = 5;
    private static final int ITERATIONS_PER_NODE = 5;
    private static final long TIME_LIMIT_MS = 8000;

    public static void main(String[] args) {
        DSMLogger.setupLogging();
        Logger.getLogger("").setLevel(Level.INFO);
        logger.setLevel(Level.WARNING);

        logger.warning("DSM-Simulation gestartet (5x5)");

        try {
            runSimulation();
            logFinalSummary();
        } catch (Exception e) {
            logger.severe("Fehler: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private static void runSimulation() throws InterruptedException {
        DSMLogger.logResult("DSM-Simulation mit " + NODE_COUNT + " Knoten und " + ITERATIONS_PER_NODE + " Iterationen");
        DSMLogger.logResult("CAP-Theorem Demonstration");

        runSingleTest("AP (Availability & Partition Tolerance)", DSMType.AP);
        Thread.sleep(1000);
        Simulator.getInstance().shutdown();
        Thread.sleep(500);

        runSingleTest("CP (Consistency & Partition Tolerance)", DSMType.CP);
        Thread.sleep(1000);
        Simulator.getInstance().shutdown();
        Thread.sleep(500);

        runSingleTest("CA (Consistency & Availability)", DSMType.CA);
    }

    private static void runSingleTest(String name, DSMType type) throws InterruptedException {
        DSMLogger.startResultSection("Test: " + name);
        logger.warning("=== Starte " + name + " ===");

        String[] nodeIds = createNodeIds();
        List<CounterApp> apps = createApps(nodeIds, type);

        try {
            initializeApps(apps, name);
            executeSimulation(apps, name);
            collectResults(apps, type);
        } finally {
            shutdownApps(apps, name);
        }
    }

    private static String[] createNodeIds() {
        String[] nodeIds = new String[NODE_COUNT];
        for (int i = 0; i < NODE_COUNT; i++) {
            nodeIds[i] = "node" + i;
        }
        return nodeIds;
    }

    private static List<CounterApp> createApps(String[] nodeIds, DSMType type) {
        List<CounterApp> apps = new ArrayList<>();

        for (int i = 0; i < NODE_COUNT; i++) {
            AbstractDSM dsm = createDSM(type, nodeIds[i]);
            CounterApp app = new CounterApp(nodeIds[i], dsm, nodeIds, ITERATIONS_PER_NODE, type.name());

            for (String otherNodeId : nodeIds) {
                if (!otherNodeId.equals(nodeIds[i])) {
                    dsm.addKnownNode(otherNodeId);
                }
            }

            apps.add(app);
            app.initializeOnly();
        }

        return apps;
    }

    private static AbstractDSM createDSM(DSMType type, String nodeId) {
        return switch (type) {
            case AP -> new APDSM(nodeId);
            case CP -> new CPDSM(nodeId);
            case CA -> new CADSM(nodeId);
            default -> throw new IllegalArgumentException("Ungültiger Typ: " + type);
        };
    }

    private static void initializeApps(List<CounterApp> apps, String name) throws InterruptedException {
        logger.warning("Initialisiere " + name);
        Thread.sleep(800);
    }

    private static void executeSimulation(List<CounterApp> apps, String name) throws InterruptedException {
        logger.warning("Starte " + name);

        for (CounterApp app : apps) {
            app.startApplication();
            Thread.sleep(20);
        }

        waitForCompletion(apps, name);
        Thread.sleep(1500);
    }

    private static void waitForCompletion(List<CounterApp> apps, String name) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        boolean allFinished = false;

        while (!allFinished && (System.currentTimeMillis() - startTime < TIME_LIMIT_MS)) {
            allFinished = true;
            for (CounterApp app : apps) {
                if (app.getCurrentIteration() < app.getTotalIterations()) {
                    allFinished = false;
                    break;
                }
            }
            Thread.sleep(200);
        }

        if (allFinished) {
            logger.warning(name + ": Alle Apps fertig");
        } else {
            logger.warning(name + ": Zeitlimit erreicht");
        }
    }

    private static void collectResults(List<CounterApp> apps, DSMType type) {
        int totalReads = 0, totalWrites = 0, totalInconsistencies = 0;

        for (CounterApp app : apps) {
            int reads = app.getReadOperations();
            int writes = app.getWriteOperations();
            int inconsistencies = app.getDetectedInconsistencies();

            totalReads += reads;
            totalWrites += writes;
            totalInconsistencies += inconsistencies;

            DSMLogger.logResult("[" + type + "] Knoten " + app.getNodeId() + ": " + reads + " Lese-Operationen, "
                    + writes + " Schreib-Operationen, " + inconsistencies + " Inkonsistenzen erkannt");
        }

        logSummary(type, totalReads, totalWrites, totalInconsistencies);
        logInterpretation(type, totalInconsistencies);
    }

    private static void logSummary(DSMType type, int totalReads, int totalWrites, int totalInconsistencies) {
        DSMLogger.logResult("\n[" + type + "] ZUSAMMENFASSUNG:");
        DSMLogger.logResult("[" + type + "] Gesamtzahl Leseoperationen: " + totalReads);
        DSMLogger.logResult("[" + type + "] Gesamtzahl Schreiboperationen: " + totalWrites);
        DSMLogger.logResult("[" + type + "] Gesamtzahl erkannter Inkonsistenzen: " + totalInconsistencies);

        if (totalReads > 0 && totalWrites > 0) {
            double readWriteRatio = (double) totalReads / totalWrites;
            DSMLogger.logResult("[" + type + "] Lese-Schreib-Verhältnis: " + String.format("%.2f", readWriteRatio));
            DSMLogger.logResult("[" + type + "] Operationen pro Knoten: " + (totalReads + totalWrites) / NODE_COUNT);
        }
    }

    private static void logInterpretation(DSMType type, int totalInconsistencies) {
        switch (type) {
            case AP:
                if (totalInconsistencies > 0) {
                    DSMLogger.logResult("[" + type + "] BESTÄTIGT: " + totalInconsistencies +
                            " Inkonsistenzen festgestellt (typisch für AP-Systeme).");
                } else {
                    DSMLogger.logResult("[" + type + "] UNGEWÖHNLICH: Keine Inkonsistenzen festgestellt.");
                }
                break;
            case CP:
            case CA:
                if (totalInconsistencies == 0) {
                    DSMLogger.logResult("[" + type + "] BESTÄTIGT: Keine Inkonsistenzen (Konsistenz garantiert).");
                } else {
                    DSMLogger.logResult("[" + type + "] FEHLER: " + totalInconsistencies +
                            " Inkonsistenzen trotz Konsistenzgarantie!");
                }
                break;
        }
    }

    private static void shutdownApps(List<CounterApp> apps, String name) throws InterruptedException {
        logger.warning("Fahre " + name + " herunter");
        for (CounterApp app : apps) {
            try {
                app.stopApplication();
            } catch (Exception e) {
                logger.warning("Problem beim Beenden: " + e.getMessage());
            }
        }
        logger.warning("=== " + name + " abgeschlossen ===");
        Thread.sleep(500);
    }

    private static void logFinalSummary() {
        DSMLogger.startResultSection("Fazit");
        DSMLogger.logResult("CAP-Theorem demonstriert:");
        DSMLogger.logResult("- AP: Verfügbar, aber inkonsistent");
        DSMLogger.logResult("- CP: Konsistent, aber blockiert bei Partitionen");
        DSMLogger.logResult("- CA: Konsistent und verfügbar ohne Partitionen");
    }

    private static void shutdown() {
        try {
            logger.warning("Fahre Simulator herunter");
            Simulator.getInstance().shutdown();
            Thread.sleep(300);
            logger.warning("Simulator heruntergefahren");
        } catch (Exception e) {
            logger.warning("Problem beim Herunterfahren: " + e.getMessage());
        }

        DSMLogger.closeLogging();
        logger.warning("DSM-Simulation beendet");
        System.exit(0);
    }

    private enum DSMType {
        AP, CP, CA
    }
}
