import os
import random
import socket
import struct
import sys
import time

# Füge src-Verzeichnis zum Python-Pfad hinzu
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from config_utils import (
    BASE_PORT, DEFAULT_N, DEFAULT_P, DEFAULT_K,
    MULTICAST_GROUP, MULTICAST_PORT,
    log, log_stat
)


def create_token_socket(port):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    # macOS: sofortiges Reuse erlauben
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
    except AttributeError:
        pass
    sock.bind(('127.0.0.1', port))
    sock.settimeout(1.0)
    return sock


def create_multicast_socket():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
    except AttributeError:
        pass
    try:
        sock.bind(('', MULTICAST_PORT))
    except OSError:
        pass
    mreq = struct.pack("4sl",
                       socket.inet_aton(MULTICAST_GROUP),
                       socket.INADDR_ANY)
    sock.setsockopt(socket.IPPROTO_IP,
                    socket.IP_ADD_MEMBERSHIP,
                    mreq)
    return sock


def send_token(next_port, zero_rounds, firework_flag, round_counter):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    msg = f"TOKEN:{zero_rounds}:{firework_flag}:{round_counter}".encode()
    sock.sendto(msg, ('127.0.0.1', next_port))
    sock.close()


def send_shutdown(next_port):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    msg = "SHUTDOWN:0".encode()
    sock.sendto(msg, ('127.0.0.1', next_port))
    sock.close()


def send_firework_multicast(process_id):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, 1)
    msg = f"FEUERWERK von P{process_id}".encode()
    sock.sendto(msg, (MULTICAST_GROUP, MULTICAST_PORT))
    sock.close()


def main(process_id, n, base_port, start_p, k, multicast_group, multicast_port):
    my_port = base_port + process_id
    next_id = (process_id + 1) % n
    next_port = base_port + next_id

    p = start_p
    round_counter = 0  # Die aktuelle Runde (nur Startprozess zählt hoch)
    zero_rounds = 0  # Wie viele Runden in Folge wurde NICHT gezündet

    firework_socket = create_multicast_socket()
    token_socket = create_token_socket(my_port)

    # Statistik
    total_fireworks = 0
    last_round_start = None
    round_times = []

    IS_START = process_id == 0

    if IS_START:
        log(process_id, "Starte den Ring und sende erstes Token.")
        time.sleep(1.5)
        last_round_start = time.time()
        send_token(next_port, zero_rounds=0, firework_flag=0, round_counter=1)

    running = True
    while running:
        try:
            data, addr = token_socket.recvfrom(1024)
            msg = data.decode()
            if msg.startswith("TOKEN:"):
                parts = msg.split(":")
                zero_rounds = int(parts[1])
                firework_flag = int(parts[2])
                token_round = int(parts[3])

                log(process_id,
                    f"Token empfangen! [zero_rounds={zero_rounds}, firework_flag={firework_flag}, Runde={token_round}]")

                # Rundenzeit messen (nur Startprozess)
                if IS_START:
                    now = time.time()
                    if last_round_start is not None:
                        round_time = now - last_round_start
                        round_times.append(round_time)
                    last_round_start = now

                log(process_id, f"Runde {token_round} gestartet (p={p:.3f})")

                # Zündentscheidung
                zündet = random.random() < p
                if zündet:
                    firework_flag = 1
                    total_fireworks += 1
                    log(process_id, ">>> FEUERWERK gezündet! (Multicast an alle)")
                    send_firework_multicast(process_id)
                else:
                    log(process_id, "... kein Feuerwerk gezündet.")

                if IS_START and last_round_start is not None:
                    log_stat(process_id, token_round, round_times, 1 if zündet else 0)

                p = p / 2

                if IS_START:
                    if round_counter == 0:
                        round_counter = token_round
                    else:
                        round_counter += 1

                    if firework_flag == 0:
                        zero_rounds += 1
                        log(process_id, f"Eine Runde ohne Feuerwerk abgeschlossen. [zero_rounds={zero_rounds}]")
                    else:
                        zero_rounds = 0
                        log(process_id, "Feuerwerk in dieser Runde gezündet. [zero_rounds=0]")

                    if zero_rounds >= k:
                        log(process_id, f"System terminiert nach {k} vollen Runden ohne Feuerwerk.")
                        # Schreibe Endstatistik
                        if round_times:
                            min_r = min(round_times)
                            max_r = max(round_times)
                            avg_r = sum(round_times) / len(round_times)
                        else:
                            min_r = max_r = avg_r = 0.0
                        with open("data/summary.csv", "w") as f:
                            f.write("total_rounds,total_fireworks,min_round_time,avg_round_time,max_round_time\n")
                            f.write("{},{},{:.6f},{:.6f},{:.6f}\n".format(
                                round_counter,
                                total_fireworks,
                                min_r,
                                avg_r,
                                max_r
                            ))
                        send_shutdown(next_port)
                        break

                    firework_flag = 0  # für nächste Runde zurücksetzen

                send_token(next_port, zero_rounds, firework_flag, token_round if not IS_START else round_counter)

            elif msg.startswith("SHUTDOWN:"):
                log(process_id, f"SHUTDOWN empfangen. Gebe weiter und terminiere.")
                send_shutdown(next_port)
                break

            else:
                continue
        except socket.timeout:
            continue

    token_socket.close()
    firework_socket.close()
    log(process_id, "Prozess terminiert.")
    sys.exit(0)


if __name__ == "__main__":
    process_id = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    n = int(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_N
    base_port = int(sys.argv[3]) if len(sys.argv) > 3 else BASE_PORT
    start_p = float(sys.argv[4]) if len(sys.argv) > 4 else DEFAULT_P
    k = int(sys.argv[5]) if len(sys.argv) > 5 else DEFAULT_K
    multicast_group = MULTICAST_GROUP
    multicast_port = MULTICAST_PORT

    main(process_id, n, base_port, start_p, k, multicast_group, multicast_port)
