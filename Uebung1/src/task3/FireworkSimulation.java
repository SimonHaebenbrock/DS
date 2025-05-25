package org.oxoo2a.sim4da.task3;

import org.oxoo2a.sim4da.Simulator;

import java.io.IOException;

/**
 * Hauptklasse für die Feuerwerk-Simulation.
 * Startet die Simulation mit den gegebenen Parametern.
 * @ author simon
 */
public class FireworkSimulation {
    public static void main(String[] args) throws IOException {
        // Parameter: n p k, default simple values, sonst gemäß Test-Matrix
        int    n = (args.length > 0 ? Integer.parseInt(args[0]) : 5);
        double p = (args.length > 1 ? Double.parseDouble(args[1]) : 0.5);
        int    k = (args.length > 2 ? Integer.parseInt(args[2]) : 3);

        // Erstellen der Nodes
        for (int i = 0; i < n; i++) {
            new FireworkNode(i, n, p, k);
        }

        // Simulation starten
        Simulator sim = Simulator.getInstance();
        sim.simulate();
        sim.shutdown();

        System.out.println("Task3 beendet. Zusammenfassung in data/summary_task3.csv");
    }
}
