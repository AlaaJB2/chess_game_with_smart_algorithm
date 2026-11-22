"""
Java <-> Python bridge (DO NOT put chess logic here; edit search.py instead).

Protocol: one line in, one line out, over stdin/stdout.
    Java   -> Python :  a FEN string                      e.g.  "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
    Python -> Java   :  one move in UCI notation          e.g.  "e7e5"   (promotion: "e7e8q")
    Java   -> Python :  "quit"  to stop the process

Python prints "READY" once after start-up so Java knows it is alive.
Anything you print() for debugging goes to stderr (the IntelliJ console), never to the protocol.
"""
import sys

# Protect the protocol: a stray print() in search.py must not corrupt the answer line.
_protocol_out = sys.stdout
sys.stdout = sys.stderr

from search import choose_move   # import errors show up immediately, before READY


def reply(text):
    _protocol_out.write(text + "\n")
    _protocol_out.flush()


def main():
    reply("READY")
    for line in sys.stdin:
        fen = line.strip()
        if not fen:
            continue
        if fen == "quit":
            break
        try:
            move = choose_move(fen)
        except Exception as e:                       # never die silently
            print("engine error:", repr(e), file=sys.stderr, flush=True)
            move = "0000"                             # Java will use a fallback move
        reply(str(move))


if __name__ == "__main__":
    main()
