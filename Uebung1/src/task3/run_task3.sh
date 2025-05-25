#!/usr/bin/env bash
set -euo pipefail

# Parameter-Arrays
PS=(0.8 0.5 0.2)
KS=(2 3 5)
NS=(5 10 25 50)

# Summary und Results
DATA_DIR="data"
SUMMARY="$DATA_DIR/summary_task3.csv"
RESULTS="$DATA_DIR/results_task3.csv"

# Java und Classpath
JAVA_CMD="/Users/simonhaebenbrock/Library/Java/JavaVirtualMachines/graalvm-ce-24.0.1/Contents/Home/bin/java"
OUT="out/production/sim4da-S25"
LIB="lib"
CP="${OUT}:${LIB}/*"

# Projekt-Root
cd "$(dirname "$0")"

# Results-File initialisieren
mkdir -p "$DATA_DIR"
if [ ! -f "$RESULTS" ]; then
  echo "n,p,k,total_rounds,total_fireworks,min_round_time,avg_round_time,max_round_time" > "$RESULTS"
fi

# compile
if [ ! -d "$OUT" ]; then
  javac -d "$OUT" -cp "lib/*" $(find src -name '*.java')
fi

# alle Kombinationen
for p in "${PS[@]}"; do
  for k in "${KS[@]}"; do
    for n in "${NS[@]}"; do
      echo "→ Simuliere n=$n, p=$p, k=$k …"

      # altes Summary löschen
      rm -f "$SUMMARY"

      # Simulation starten
      "$JAVA_CMD" -cp "$CP" org.oxoo2a.sim4da.task3.FireworkSimulation "$n" "$p" "$k"

      # Ergebnis parsen und in results_task3.csv anhängen
      if [ -f "$SUMMARY" ]; then
        tail -n +2 "$SUMMARY" | \
        awk -v n="$n" -v p="$p" -v k="$k" -F, '{ printf("%d,%.3f,%d,%s,%s,%s,%s\n", n, p, k, $1,$2,$3,$4,$5) }' \
          >> "$RESULTS"
        echo " angehängt an $RESULTS"
      else
        echo " $SUMMARY nicht gefunden!"
      fi
    done
  done
done