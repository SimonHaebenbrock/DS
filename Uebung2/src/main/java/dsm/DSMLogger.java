package main.java.dsm;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Hilfsklasse für verbessertes Logging im DSM-Projekt.
 * Ermöglicht sowohl Konsolen- als auch Datei-Logging mit verschiedenen Detailstufen.
 */
public class DSMLogger {
    private static FileHandler fileHandler;
    private static PrintWriter resultsWriter;

    /**
     * Initialisiert das Logging-System für das DSM-Projekt.
     * Erstellt sowohl eine detaillierte Log-Datei als auch eine Ergebnisdatei.
     */
    public static void setupLogging() {
        try {
            // Dateiname mit aktuellem Zeitstempel
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());

            // Detaillierte Logdatei einrichten
            fileHandler = new FileHandler("dsm_simulation_" + timestamp + ".log");
            fileHandler.setFormatter(new SimpleFormatter());

            // Root-Logger konfigurieren
            Logger rootLogger = Logger.getLogger("");
            rootLogger.setLevel(Level.INFO);

            // ConsoleHandler auf WARNING-Level setzen, um nur wichtige Meldungen in der Konsole zu sehen
            for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
                if (handler instanceof ConsoleHandler) {
                    handler.setLevel(Level.WARNING);
                }
            }

            // FileHandler hinzufügen für detailliertes Logging in Datei
            rootLogger.addHandler(fileHandler);

            // Ergebnisdatei für Zusammenfassung einrichten
            resultsWriter = new PrintWriter(new FileWriter("dsm_results_" + timestamp + ".txt"));
            resultsWriter.println("=== DSM-Simulation Ergebnisse ===");
            resultsWriter.println("Zeitstempel: " + new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date()));
            resultsWriter.println("\n");

        } catch (IOException e) {
            System.err.println("Fehler beim Einrichten des Loggings: " + e.getMessage());
        }
    }

    /**
     * Schreibt einen Eintrag in die Ergebnisdatei.
     *
     * @param text Der zu schreibende Text
     */
    public static void logResult(String text) {
        if (resultsWriter != null) {
            resultsWriter.println(text);
            resultsWriter.flush(); // Sofortiges Schreiben in die Datei
        }
    }

    /**
     * Schließt die Logging-Ressourcen.
     */
    public static void closeLogging() {
        if (fileHandler != null) {
            fileHandler.close();
        }

        if (resultsWriter != null) {
            resultsWriter.close();
        }
    }

    /**
     * Beginnt einen neuen Abschnitt in der Ergebnisdatei.
     *
     * @param title Der Titel des neuen Abschnitts
     */
    public static void startResultSection(String title) {
        logResult("\n\n=== " + title + " ===");
    }

    /**
     * Schreibt eine Zusammenfassung für eine bestimmte DSM-Variante.
     *
     * @param title              Name der DSM-Variante
     * @param inconsistencyCount Anzahl erkannter Inkonsistenzen
     * @param operationCount     Gesamtzahl der Operationen
     */
    public static void writeVariantSummary(String title, int inconsistencyCount, int operationCount) {
        logResult("\nZusammenfassung für " + title + ":");
        logResult("- Gesamtzahl Operationen: " + operationCount);
        logResult("- Erkannte Inkonsistenzen: " + inconsistencyCount);
        logResult("- Inkonsistenz-Rate: " + String.format("%.2f%%", (inconsistencyCount * 100.0 / operationCount)));
    }
}
