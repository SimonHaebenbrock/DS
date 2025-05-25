package org.oxoo2a.sim4da.task3;

import org.oxoo2a.sim4da.Message;
import org.oxoo2a.sim4da.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Eignene Node-Klasse für die Feuerwerk-Simulation.
 * @ author simon
 */
public class FireworkNode extends Node {
    private static final Logger logger = LoggerFactory.getLogger(FireworkNode.class);

    // Parameter:
    private final double p0;
    private final int k;
    private final boolean isCoordinator;
    private final Random random = new Random();

    private int zeroRounds = 0;
    private int roundCounter = 1;
    private int totalFireworks = 0;
    private final List<Long> roundTimes = new ArrayList<>();
    private long lastRoundStart;

    public FireworkNode(int id, int n, double startP, int k) {
        super(String.valueOf(id));
        this.p0 = startP;
        this.k = k;
        this.isCoordinator = (id == 0);
    }

    /**
     * Startet die Simulation, wenn alle Nodes instanziiert sind.
     * @see org.oxoo2a.sim4da.Simulator#simulate()
     */
    @Override
    protected void engage() {
        // Erstes Token nur vom Coordinator
        if (isCoordinator) {
            lastRoundStart = System.nanoTime();
            Message init = new Message()
                    .add("token", String.valueOf(roundCounter))
                    .addHeader("firework", "0")
                    .addHeader("sender", NodeName());
            broadcast(init);
            logger.info("Starte Runde {}", roundCounter);
        }

        // solange die Simulation läuft, empfange Nachrichten
        while (true) {
            Message m = receive();
            String tok = m.query("token");
            if ("end".equals(tok)) {
                // Abbruch
                logger.info("Ende empfangen, beende Node {}", NodeName());
                break;
            }

            int token = Integer.parseInt(tok);
            long now = System.nanoTime();
            if (isCoordinator) {
                roundTimes.add(now - lastRoundStart);
                lastRoundStart = now;
            }
            logger.info("Runde {} empfangen (zeroRounds={})", token, zeroRounds);

            // Berechne, ob Feuerwerk gezündet wird
            double p = p0 / Math.pow(2, token - 1);
            boolean fired = random.nextDouble() < p;
            if (fired) {
                totalFireworks++;
                logger.info(">> FEUERWERK in Runde {}!", token);
            }

            // Wenn es ein Feuerwerk gab, resetten wir die zeroRounds
            if (isCoordinator) {
                if (fired) zeroRounds = 0;
                else zeroRounds++;
                if (zeroRounds >= k) {
                    writeSummary(roundCounter, totalFireworks, roundTimes);
                    // Abbruchnachricht an alle
                    Message end = new Message()
                            .add("token", "end")
                            .addHeader("sender", NodeName())
                            .addHeader("sender", NodeName());
                    broadcast(end);
                    logger.info("Abbruchbedingung erreicht, sende 'end'.");
                    break;
                }
                roundCounter++;
            }

            // Token weiterreichen
            Message out = new Message()
                    .add("token", String.valueOf(token + 1))
                    .addHeader("firework", fired ? "1" : "0");
            broadcast(out);
        }
    }

    // Schreibt die Zusammenfassung in eine CSV-Datei
    private void writeSummary(int totalRounds, int totalFireworks, List<Long> times) {
        Path dir = Paths.get("data");
        try {
            Files.createDirectories(dir);
            Path out = dir.resolve("summary_task3.csv");
            try (BufferedWriter w = Files.newBufferedWriter(out)) {
                w.write("total_rounds,total_fireworks,min_round_time,avg_round_time,max_round_time\n");
                long min = times.isEmpty() ? 0 : Collections.min(times);
                long max = times.isEmpty() ? 0 : Collections.max(times);
                double avg = times.isEmpty() ? 0
                        : times.stream().mapToLong(Long::longValue).average().orElse(0);
                // Nanosekunden → Sekunden
                double min_s = min / 1e9;
                double avg_s = avg / 1e9;
                double max_s = max / 1e9;
                w.write(String.format("%d,%d,%.6f,%.6f,%.6f\n",
                        totalRounds, totalFireworks,
                        min_s, avg_s, max_s
                ));
                logger.info("Summary geschrieben: rounds={}, fireworks={}, min={}s, avg={}s, max={}s",
                        totalRounds, totalFireworks, min_s, avg_s, max_s);
            }
        } catch (IOException e) {
            logger.error("Fehler beim Schreiben der Summary", e);
        }
    }
}


