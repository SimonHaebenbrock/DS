# Übung 1 – Verteilte Systeme (Sommer 2025)
Author: Simon Haebenbrock

## Projektstruktur
- `src/`: Quellcode-Verzeichnis
  - `process.py`: Implementierung eines einzelnen Prozesses im Token-Ring
  - `run_experiments.py`: Hauptscript für systematische Tests
  - `config_utils.py`: Konfiguration und Hilfsfunktionen
  - `plot_results.py`: Visualisierung der Ergebnisse
- `data/`: Ausgabeverzeichnis für CSV-Dateien
  - `summary.csv`: Zusammenfassung eines einzelnen Laufs
  - `results.csv`: Ergebnisse der Fixed-Tests
  - `max_n_summary.csv`: Ergebnisse der Max-n-Suche
  - `stats_P0.csv`: Detaillierte Statistiken des Startprozesses
- `plots/`: Visualisierungen der Ergebnisse
- `docs/`: Dokumentation und Aufgabenstellung

## Verwendung

### Systematische Tests
```bash
# Führt beide Tests durch (Fixed-Tests und Max-n-Suche)
python src/task1/run_experiments.py

# Nur Fixed-Tests mit vordefinierten Parametern
python src/task1/run_experiments.py --fixed-test-only

# Nur Max-n-Suche für p=0.5, k=3
python src/task1/run_experiments.py --max-only
```

### Parameter
Die Standardkonfiguration kann in `config_utils.py` angepasst werden:
- `DEFAULT_N`: Anzahl der Prozesse (Standard: 4)
- `DEFAULT_P`: Startwahrscheinlichkeit (Standard: 0.5)
- `DEFAULT_K`: Terminierungsschwelle (Standard: 3)

### Ausgaben
- Fixed-Tests erzeugen `data/results.csv`
- Max-n-Suche erzeugt `data/max_n_summary.csv`
- Visualisierungen werden im `plots/` Verzeichnis gespeichert
