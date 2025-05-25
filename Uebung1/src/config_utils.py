# Konfiguration für das Experiment
BASE_PORT = 50000
DEFAULT_N = 4
DEFAULT_P = 0.5
DEFAULT_K = 3
MULTICAST_GROUP = "224.1.1.1"
MULTICAST_PORT = 50010

NS = [5, 10, 25, 50]  # Fixed-Tests: Anzahl der Prozesse
PS = [0.8, 0.5, 0.2]  # Fixed-Tests: Startwahrscheinlichkeiten
KS = [2, 3, 5]  # Fixed-Tests: K-Werte

# Für Spezial-Max-n (p=0.5, k=3):
ROUGH_START = 140  # ab hier starten
ROUGH_STEP = 10  # Zehnerschritte

# --- Experiment-Konfiguration ---------------------------------------------
TIMEOUT = 30.0  # Max Sekunden pro Lauf
SUMMARY_FILE = "data/summary.csv"
RESULTS_FILE = "data/results.csv"
MAX_N_FILE = "data/max_n_summary.csv"

import datetime
import os

""" Helfer-Funktionen zum Logging und Speichern von Statistiken """
def log(process_id, msg):
    now = datetime.datetime.now().strftime("%H:%M:%S.%f")[:-3]
    print(f"[{now}] [P{process_id}] {msg}")

""" Loggt die Rundenstatistik in eine CSV-Datei für einen Prozess """
def log_stat(pid, round_nr, rtime, zündet):
    os.makedirs("data", exist_ok=True)  # Stelle sicher, dass der data Ordner existiert
    with open(f"data/stats_P{pid}.csv", "a") as f:
        f.write(f"{round_nr},{rtime},{int(zündet)}\n")
