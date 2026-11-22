"""
THIS is the file the search team edits (Python students).

Contract:  choose_move(fen: str) -> str
    fen : the current position, side to move included
    returns the best move for the side to move, in UCI ("e2e4", "e7e8q" for promotion)

Requires:  pip install chess      (python-chess: board, legal moves, check detection)
Right now it only plays a random legal move, so the whole pipeline can be tested.
Replace the body with the depth-limited search (DFS / minimax / alpha-beta) + evaluation.
"""
import random
import chess


def choose_move(fen: str) -> str:
    board = chess.Board(fen)
    moves = list(board.legal_moves)
    return random.choice(moves).uci()
