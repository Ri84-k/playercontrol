"""Local, temporary GitHub Actions check for PlayerControl and Carpet fake players."""
from __future__ import annotations

import socket
import struct
import time
from pathlib import Path


def read_exact(sock: socket.socket, count: int) -> bytes:
    result = bytearray()
    while len(result) < count:
        part = sock.recv(count - len(result))
        if not part:
            raise ConnectionError("RCON connection closed unexpectedly")
        result.extend(part)
    return bytes(result)


class Rcon:
    def __init__(self, password: str):
        self.sock = socket.create_connection(("127.0.0.1", 25575), timeout=8)
        self.sock.settimeout(15)
        self.request_id = 100
        self.send(3, password)
        for _ in range(4):
            ident, kind, _ = self.receive()
            if ident == -1:
                raise RuntimeError("RCON authentication was rejected")
            if ident == self.request_id and kind == 2:
                break
        else:
            raise RuntimeError("RCON authentication did not finish")

    def send(self, kind: int, text: str) -> None:
        body = struct.pack("<ii", self.request_id, kind) + text.encode("utf-8") + b"\0\0"
        self.sock.sendall(struct.pack("<i", len(body)) + body)

    def receive(self) -> tuple[int, int, str]:
        size = struct.unpack("<i", read_exact(self.sock, 4))[0]
        if size < 10 or size > 65536:
            raise RuntimeError(f"Unexpected RCON packet length: {size}")
        body = read_exact(self.sock, size)
        ident, kind = struct.unpack("<ii", body[:8])
        return ident, kind, body[8:-2].decode("utf-8", errors="replace")

    def command(self, text: str) -> str:
        self.request_id += 1
        self.send(2, text)
        for _ in range(4):
            ident, kind, output = self.receive()
            if ident == self.request_id and kind == 0:
                print(f"> {text}\n{output}", flush=True)
                return output
        raise RuntimeError(f"No RCON response for {text}")


def main() -> None:
    settings = Path("run/server.properties").read_text(encoding="utf-8").splitlines()
    password = next(line.partition("=")[2] for line in settings if line.startswith("rcon.password="))
    rcon = Rcon(password)
    rcon.command("carpet allowSpawningOfflinePlayers true")
    rcon.command("player TestBotA spawn")
    rcon.command("player TestBotB spawn")

    deadline = time.monotonic() + 45
    while True:
        online = rcon.command("list")
        if "TestBotA" in online and "TestBotB" in online:
            break
        if time.monotonic() > deadline:
            raise RuntimeError("Carpet did not spawn both fake players")
        time.sleep(3)

    before = rcon.command("execute as TestBotA run controlPlayer status")
    swapped = rcon.command("execute as TestBotA run controlPlayer swap TestBotB")
    after = rcon.command("execute as TestBotA run controlPlayer status")
    restored = rcon.command("execute as TestBotA run controlPlayer release")
    final = rcon.command("execute as TestBotA run controlPlayer status")

    bad = ("unknown or incomplete command", "an unexpected error", "could not execute")
    for name, output in (("initial status", before), ("swap", swapped), ("swapped status", after),
                         ("release", restored), ("restored status", final)):
        if not output.strip() or any(fragment in output.lower() for fragment in bad):
            raise RuntimeError(f"{name} failed: {output!r}")

    print("Carpet fake-player spawn, swap, release and status commands completed.", flush=True)


if __name__ == "__main__":
    main()
