#!/usr/bin/env python3
import subprocess
import time
import os
import csv
import sys

# Füge src-Verzeichnis zum Python-Pfad hinzu
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from config_utils import (
    NS, PS, KS,
    ROUGH_START, ROUGH_STEP,
    BASE_PORT, TIMEOUT,
    SUMMARY_FILE, RESULTS_FILE, MAX_N_FILE
)


# --- 3) Helfer -------------------------------------------------------------
def cleanup(path):
    try:
        os.remove(path)
    except FileNotFoundError:
        pass


def kill_and_wait(procs):
    for p in procs:
        if p.poll() is None:
            p.terminate()
    time.sleep(0.2)
    for p in procs:
        if p.poll() is None:
            p.kill()
    for p in procs:
        try:
            p.wait(timeout=1)
        except subprocess.TimeoutExpired:
            pass
    time.sleep(0.5)


def run_ring(n, p, k):
    """Startet n Prozesse, wartet auf SUMMARY_FILE oder TIMEOUT."""
    cleanup(SUMMARY_FILE)
    procs = []
    for i in range(n):
        cmd = [
            sys.executable, "src/task1/process.py",  # Pfad korrigiert
            str(i), str(n),
            str(BASE_PORT),
            str(p),
            str(k)
        ]
        procs.append(subprocess.Popen(cmd))
    start = time.time()
    while True:
        if os.path.exists(SUMMARY_FILE):
            break
        if time.time() - start > TIMEOUT:
            kill_and_wait(procs)
            return False
        time.sleep(0.05)
    kill_and_wait(procs)
    return True


def read_summary():
    with open(SUMMARY_FILE) as f:
        row = next(csv.DictReader(f))
        return {
            "total_rounds": int(row["total_rounds"]),
            "total_fireworks": int(row["total_fireworks"]),
            "min_round_time": float(row["min_round_time"]),
            "avg_round_time": float(row["avg_round_time"]),
            "max_round_time": float(row["max_round_time"]),
        }


# --- 4) Fixed Tests --------------------------------------------------------
def fixed_tests(writer):
    for p in PS:
        for k in KS:
            for n in NS:
                print(f"Fixed   n={n}, p={p}, k={k} ...", end=" ")
                ok = run_ring(n, p, k)
                row = {"n": n, "p": p, "k": k, "success": ok}
                if ok:
                    row.update(read_summary())
                    print("OK")
                else:
                    print("FAIL")
                writer.writerow(row)


# --- 5) Max-n-Suche für p=0.5, k=3 -----------------------------------------
def find_max_n_special(writer):
    p, k = 0.5, 3
    L = None
    U = None

    # Coarse-Scan ab COARSE_START in Zehnerschritten
    n = ROUGH_START
    while True:
        print(f"Coarse n={n}, p={p}, k={k} ...", end=" ")
        if run_ring(n, p, k):
            L = n
            print("OK")
            n += ROUGH_STEP
        else:
            U = n
            print("FAIL")
            break

    # Entscheidung
    if L is None:
        max_n = 0
    elif U is None:
        # nie FAIL => max_n = letzter OK
        max_n = L
    else:
        # Fein-Scan im Intervall (L+1 ... U-1)
        max_n = L
        for m in range(L + 1, U):
            print(f"Refine n={m}, p={p}, k={k} ...", end=" ")
            if run_ring(m, p, k):
                max_n = m
                print("OK")
            else:
                print("FAIL")
                break

    writer.writerow({"p": p, "k": k, "max_n": max_n})
    print(f"→ max_n for (p={p}, k={k}) = {max_n}")


# --- 6) Main ---------------------------------------------------------------
def main():
    max_only = ("--max-only" in sys.argv)
    fixed_test_only = ("--fixed-test-only" in sys.argv)

    if max_only and fixed_test_only:
        print("Error: Kann nicht beide Optionen --max-only und --fixed-test-only gleichzeitig verwenden")
        sys.exit(1)

    # 6.1 Max-n-Suche
    if max_only:
        cleanup(MAX_N_FILE)
        cleanup("data/stats_P0.csv")
        with open(MAX_N_FILE, "w", newline="") as f2:
            w2 = csv.DictWriter(f2, fieldnames=["p", "k", "max_n"])
            w2.writeheader()
            find_max_n_special(w2)
        return

    # 6.2 Fixed-Tests
    if fixed_test_only:
        cleanup(RESULTS_FILE)
        cleanup("data/stats_P0.csv")
        with open(RESULTS_FILE, "w", newline="") as f:
            fields = [
                "n", "p", "k",
                "total_rounds", "total_fireworks",
                "min_round_time", "avg_round_time", "max_round_time",
                "success"
            ]
            w = csv.DictWriter(f, fieldnames=fields)
            w.writeheader()
            fixed_tests(w)
        return

    # 6.3 Beide Tests (Standard)
    cleanup(RESULTS_FILE)
    cleanup("data/stats_P0.csv")
    with open(RESULTS_FILE, "w", newline="") as f:
        fields = [
            "n", "p", "k",
            "total_rounds", "total_fireworks",
            "min_round_time", "avg_round_time", "max_round_time",
            "success"
        ]
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        fixed_tests(w)

    cleanup(MAX_N_FILE)
    with open(MAX_N_FILE, "w", newline="") as f2:
        w2 = csv.DictWriter(f2, fieldnames=["p", "k", "max_n"])
        w2.writeheader()
        find_max_n_special(w2)


if __name__ == "__main__":
    main()
