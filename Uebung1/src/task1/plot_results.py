import pandas as pd
import matplotlib.pyplot as plt

# 1) Daten einlesen
df = pd.read_csv("data/results.csv")

# 2) Standard-Parameter (p=0.5, k=3)
std_df = df[(df.p == 0.5) & (df.k == 3) & (df.success)]

# 3) Plot A: avg_round_time vs. n mit Annotation
plt.figure()
plt.plot(std_df.n, std_df.avg_round_time, marker='o')
for x, y in zip(std_df.n, std_df.avg_round_time):
    plt.text(x, y, f"{y:.3f}", ha='center', va='bottom')
plt.title("Einfluss von n (p=0.5, k=3)")
plt.xlabel("Anzahl Prozesse n")
plt.ylabel("Ø Rundenzeit [s]")
plt.grid(True)
plt.savefig("plots/plot_n_vs_time_annotated.png")

# 4) Plot B: avg_round_time vs. p für n=25, k=3 mit Annotation
df_p = df[(df.n == 25) & (df.k == 3) & (df.success)]
plt.figure()
plt.plot(df_p.p, df_p.avg_round_time, marker='o')
for x, y in zip(df_p.p, df_p.avg_round_time):
    plt.text(x, y, f"{y:.3f}", ha='center', va='bottom')
plt.title("Einfluss von p (n=25, k=3)")
plt.xlabel("Zünd-Wahrscheinlichkeit p")
plt.ylabel("Ø Rundenzeit [s]")
plt.grid(True)
plt.savefig("plots/plot_p_vs_time_annotated.png")

# 5) Plot C: avg_round_time vs. k für n=25, p=0.5 mit Annotation
df_k = df[(df.n == 25) & (df.p == 0.5) & (df.success)]
plt.figure()
plt.plot(df_k.k, df_k.avg_round_time, marker='o')
for x, y in zip(df_k.k, df_k.avg_round_time):
    plt.text(x, y, f"{y:.3f}", ha='center', va='bottom')
plt.title("Einfluss von k (n=25, p=0.5)")
plt.xlabel("Terminierungs-Runden k")
plt.ylabel("Ø Rundenzeit [s]")
plt.grid(True)
plt.savefig("plots/plot_k_vs_time_annotated.png")

plt.show()
