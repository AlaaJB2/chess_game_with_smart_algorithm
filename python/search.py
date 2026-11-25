"""
search.py  -  محرك البحث (Python)                     لا يحتاج أي مكتبة خارجية ولا إنترنت.

العقد مع Java (لا يتغير):   choose_move(fen: str) -> str      مثل "e2e4" أو "e7e8q"

الخوارزمية:
  * بحث بعمق أولاً (DFS) بعمق محدود: Negamax مع قص ألفا-بيتا.
        ألفا-بيتا هو minimax نفسه (نفس النتيجة عند نفس العمق) لكنه يتجاهل الفروع التي لا يمكن أن تغيّر القرار.
  * تعميق تدريجي: نبحث بعمق 1 ثم 2 ثم 3 ... حتى MAX_DEPTH أو انتهاء الوقت، ونعتمد آخر عمق اكتمل.
  * بحث الهدوء (Quiescence) عند الأوراق: لا نقيّم وضعية في منتصف عملية تبادل.
  * ترتيب النقلات (الأكل الأعلى قيمة أولاً) ليعمل القص بكفاءة.
  * دالة التقييم: قيمة القطع + جداول مواقع القطع (PST) + قواعد بسيطة للنهايات.
"""
import random
import sys
import time

# ============================================================================
#  إعدادات يمكنكم تغييرها
# ============================================================================
MAX_DEPTH = 10              # أقصى عمق (DFS محدود العمق). عملياً يصل لـ 3-5 في بايثون ضمن الوقت.
TIME_LIMIT_SECONDS = 4.0    # أقصى وقت للنقلة الواحدة (مهلة Java هي 10 ثوانٍ)
FIXED_DEPTH = None          # مثال: FIXED_DEPTH = 3  -> عمق ثابت 3 بلا حدّ زمني (للتجربة والشرح)

USE_BOOK = True             # كتاب الافتتاحيات: نقلات نظرية في البداية بدل البحث (False = بحث دائماً)
BOOK_AVOID_KNIGHT_START = True   # لا يبدأ الحاسوب بحصان (أول نقلة لكل لون تكون نقلة بيدق)

MATE = 100000
INF = 10 ** 9
MAX_PLY = 64

# ============================================================================
#  تمثيل الرقعة: مصفوفة 120 خانة (mailbox). 64 خانة حقيقية + إطار حارس OFF.
#  رقم الخانة = 21 + عمود + 10*صف     (a1 = 21 ، h1 = 28 ، a8 = 91 ، h8 = 98)
#  القطع: أبيض موجب، أسود سالب:  بيدق 1 ، حصان 2 ، فيل 3 ، قلعة 4 ، وزير 5 ، ملك 6
# ============================================================================
OFF = 99
SQUARES = [21 + f + 10 * r for r in range(8) for f in range(8)]
KNIGHT_D = (8, 12, 19, 21, -8, -12, -19, -21)
KING_D = (1, -1, 10, -10, 9, -9, 11, -11)
ROOK_D = (1, -1, 10, -10)
BISHOP_D = (9, -9, 11, -11)
MAT = [0, 100, 320, 330, 500, 900, 0]

# حقوق التبييت: بت 1 = أبيض قصير، 2 = أبيض طويل، 4 = أسود قصير، 8 = أسود طويل
CASTLE_MASK = [15] * 120
CASTLE_MASK[21] = 15 & ~2
CASTLE_MASK[25] = 15 & ~3
CASTLE_MASK[28] = 15 & ~1
CASTLE_MASK[91] = 15 & ~8
CASTLE_MASK[95] = 15 & ~12
CASTLE_MASK[98] = 15 & ~4

# ---- جداول مواقع القطع (من وجهة نظر الأبيض، الصف الأعلى = الصف الثامن) ----
PST = {
    1: [0, 0, 0, 0, 0, 0, 0, 0, 50, 50, 50, 50, 50, 50, 50, 50, 10, 10, 20, 30, 30, 20, 10, 10,
        5, 5, 10, 25, 25, 10, 5, 5, 0, 0, 0, 20, 20, 0, 0, 0, 5, -5, -10, 0, 0, -10, -5, 5,
        5, 10, 10, -20, -20, 10, 10, 5, 0, 0, 0, 0, 0, 0, 0, 0],
    2: [-50, -40, -30, -30, -30, -30, -40, -50, -40, -20, 0, 0, 0, 0, -20, -40, -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30, -30, 0, 15, 20, 20, 15, 0, -30, -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40, -50, -40, -30, -30, -30, -30, -40, -50],
    3: [-20, -10, -10, -10, -10, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10, -10, 0, 10, 10, 10, 10, 0, -10, -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 5, 0, 0, 0, 0, 5, -10, -20, -10, -10, -10, -10, -10, -10, -20],
    4: [0, 0, 0, 0, 0, 0, 0, 0, 5, 10, 10, 10, 10, 10, 10, 5, -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5, 0, 0, 0, 5, 5, 0, 0, 0],
    5: [-20, -10, -10, -5, -5, -10, -10, -20, -10, 0, 0, 0, 0, 0, 0, -10, -10, 0, 5, 5, 5, 5, 0, -10,
        -5, 0, 5, 5, 5, 5, 0, -5, 0, 0, 5, 5, 5, 5, 0, -5, -10, 5, 5, 5, 5, 5, 0, -10,
        -10, 0, 5, 0, 0, 0, 0, -10, -20, -10, -10, -5, -5, -10, -10, -20],
}
KING_MID = [-30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20, -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20, 20, 30, 10, 0, 0, 10, 30, 20]
KING_END = [-50, -40, -30, -20, -20, -30, -40, -50, -30, -20, -10, 0, 0, -10, -20, -30,
            -30, -10, 20, 30, 30, 20, -10, -30, -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30, -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -30, 0, 0, 0, 0, -30, -30, -50, -30, -30, -30, -30, -30, -30, -50]

# VAL[قطعة][خانة] = مساهمة القطعة في تقييم الأبيض (موجبة للأبيض، سالبة للأسود). فهرس سالب = قطعة سوداء.
VAL = [[0] * 120 for _ in range(13)]
KMID_V = [0] * 120      # مساهمة الملكين (تُضاف عند التقييم حسب مرحلة اللعب)
KEND_V = [0] * 120
KMID_B = [0] * 120
KEND_B = [0] * 120
CENTER_DIST = [0] * 120
for _r in range(8):
    for _f in range(8):
        _s = 21 + _f + 10 * _r
        for _p in range(1, 6):
            VAL[_p][_s] = MAT[_p] + PST[_p][(7 - _r) * 8 + _f]
            VAL[-_p][_s] = -(MAT[_p] + PST[_p][_r * 8 + _f])
        KMID_V[_s] = KING_MID[(7 - _r) * 8 + _f]
        KEND_V[_s] = KING_END[(7 - _r) * 8 + _f]
        KMID_B[_s] = -KING_MID[_r * 8 + _f]
        KEND_B[_s] = -KING_END[_r * 8 + _f]
        CENTER_DIST[_s] = max(3 - _f, _f - 4) + max(3 - _r, _r - 4)

ENDGAME_NPM = 2600      # مجموع قيم (حصان+فيل+قلعة+وزير) للّونين: أقل منه = نهاية اللعبة
START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"


class Timeout(Exception):
    pass


def sq_name(s):
    return chr(96 + s % 10) + str(s // 10 - 1)


def name_to_sq(n):
    return 21 + (ord(n[0]) - 97) + 10 * (int(n[1]) - 1)


# ============================================================================
#  الرقعة: توليد النقلات + تنفيذ/تراجع + كشف الكش
# ============================================================================
class Board:
    def __init__(self, fen):
        self.sq = [OFF] * 120
        for s in SQUARES:
            self.sq[s] = 0
        self.king_sq = [0, 0]
        self.stack = []
        self.score = 0          # قيمة القطع+مواقعها (بدون الملوك) من وجهة نظر الأبيض
        self.npm = 0            # مجموع قيم الحصان/الفيل/القلعة/الوزير للّونين
        self.pawns = 0
        parts = fen.split()
        rows = parts[0].split("/")
        for i, row in enumerate(rows):
            rank, file = 7 - i, 0
            for ch in row:
                if ch.isdigit():
                    file += int(ch)
                    continue
                kind = "pnbrqk".index(ch.lower()) + 1
                piece = kind if ch.isupper() else -kind
                s = 21 + file + 10 * rank
                self.sq[s] = piece
                if kind == 6:
                    self.king_sq[0 if piece > 0 else 1] = s
                else:
                    self.score += VAL[piece][s]
                    if kind == 1:
                        self.pawns += 1
                    else:
                        self.npm += MAT[kind]
                file += 1
        self.side = 1 if parts[1] == "w" else -1
        c = parts[2]
        self.castle = (1 if "K" in c else 0) | (2 if "Q" in c else 0) | (4 if "k" in c else 0) | (8 if "q" in c else 0)
        self.ep = 0 if parts[3] == "-" else name_to_sq(parts[3])
        self.halfmove = int(parts[4]) if len(parts) > 4 else 0
        self.fullmove = int(parts[5]) if len(parts) > 5 else 1

    # ---------------------------------------------------------------- الهجمات
    def is_attacked(self, s, by):
        """هل الخانة s مهاجَمة بقطعة من اللون by (1 أبيض، -1 أسود)؟"""
        b = self.sq
        if by == 1:
            if b[s - 9] == 1 or b[s - 11] == 1:
                return True
        else:
            if b[s + 9] == -1 or b[s + 11] == -1:
                return True
        n = 2 * by
        for d in KNIGHT_D:
            if b[s + d] == n:
                return True
        k = 6 * by
        for d in KING_D:
            if b[s + d] == k:
                return True
        q = 5 * by
        r = 4 * by
        for d in ROOK_D:
            x = s + d
            p = b[x]
            while p == 0:
                x += d
                p = b[x]
            if p == r or p == q:
                return True
        bi = 3 * by
        for d in BISHOP_D:
            x = s + d
            p = b[x]
            while p == 0:
                x += d
                p = b[x]
            if p == bi or p == q:
                return True
        return False

    def in_check(self):
        side = self.side
        return self.is_attacked(self.king_sq[0 if side == 1 else 1], -side)

    # ---------------------------------------------------------------- توليد النقلات
    def gen_moves(self, caps_only=False):
        """نقلات شبه قانونية (قد تترك الملك في كش؛ تُفحص بعد التنفيذ). كل نقلة: (ترتيب، من، إلى، ترقية)."""
        b = self.sq
        side = self.side
        out = []
        add = out.append
        for s in SQUARES:
            p = b[s]
            if p * side <= 0:
                continue
            ap = p if p > 0 else -p
            if ap == 1:
                d = 10 * side
                t = s + d
                promo_rank = (t > 90) if side == 1 else (t < 29)
                if b[t] == 0 and (not caps_only or promo_rank):
                    if promo_rank:
                        for pr in (5, 4, 3, 2):
                            add((8000 + MAT[pr], s, t, pr))
                    else:
                        add((0, s, t, 0))
                        start = (30 < s < 39) if side == 1 else (80 < s < 89)
                        if start and b[t + d] == 0:
                            add((0, s, t + d, 0))
                for dd in (d - 1, d + 1):
                    t = s + dd
                    c = b[t]
                    if c == OFF:
                        continue
                    if c * side < 0:
                        v = 10 * MAT[c if c > 0 else -c] - 1 + 10000
                        if promo_rank:
                            for pr in (5, 4, 3, 2):
                                add((v + MAT[pr], s, t, pr))
                        else:
                            add((v, s, t, 0))
                    elif c == 0 and t == self.ep:
                        add((10000 + 999, s, t, 0))
            elif ap == 2 or ap == 6:
                for dd in (KNIGHT_D if ap == 2 else KING_D):
                    t = s + dd
                    c = b[t]
                    if c == OFF or c * side > 0:
                        continue
                    if c != 0:
                        add((10 * MAT[c if c > 0 else -c] - ap + 10000, s, t, 0))
                    elif not caps_only:
                        add((0, s, t, 0))
            else:
                for dd in (BISHOP_D if ap == 3 else ROOK_D if ap == 4 else KING_D):
                    t = s + dd
                    c = b[t]
                    while c == 0:
                        if not caps_only:
                            add((0, s, t, 0))
                        t += dd
                        c = b[t]
                    if c != OFF and c * side < 0:
                        add((10 * MAT[c if c > 0 else -c] - ap + 10000, s, t, 0))
        if not caps_only:
            self._gen_castling(out)
        return out

    def _gen_castling(self, out):
        side = self.side
        if not self.castle:
            return
        b = self.sq
        base = 21 if side == 1 else 91
        bits = (1, 2) if side == 1 else (4, 8)
        if not (self.castle & (bits[0] | bits[1])) or b[base + 4] != 6 * side:
            return
        foe = -side
        if self.is_attacked(base + 4, foe):
            return
        if (self.castle & bits[0]) and b[base + 7] == 4 * side and b[base + 5] == 0 and b[base + 6] == 0 \
                and not self.is_attacked(base + 5, foe) and not self.is_attacked(base + 6, foe):
            out.append((0, base + 4, base + 6, 0))
        if (self.castle & bits[1]) and b[base] == 4 * side and b[base + 1] == 0 and b[base + 2] == 0 and b[base + 3] == 0 \
                and not self.is_attacked(base + 3, foe) and not self.is_attacked(base + 2, foe):
            out.append((0, base + 4, base + 2, 0))

    # ---------------------------------------------------------------- تنفيذ / تراجع
    def make(self, f, t, promo):
        b = self.sq
        piece = b[f]
        cap = b[t]
        side = self.side
        self.stack.append((f, t, piece, cap, self.castle, self.ep, self.halfmove, self.score, self.npm, self.pawns))
        sc = self.score - VAL[piece][f]
        ap = piece if piece > 0 else -piece
        new_ep = 0
        reset = cap != 0
        if cap:
            sc -= VAL[cap][t]
            ac = cap if cap > 0 else -cap
            if ac == 1:
                self.pawns -= 1
            elif ac != 6:
                self.npm -= MAT[ac]
        b[f] = 0
        if ap == 1:
            reset = True
            d = t - f
            if cap == 0 and (d == 9 or d == 11 or d == -9 or d == -11):      # أخذ بالمرور
                cp = t - 10 * side
                sc -= VAL[b[cp]][cp]
                b[cp] = 0
                self.pawns -= 1
            elif d == 20 or d == -20:
                new_ep = f + d // 2
            if promo:
                np_ = promo * side
                b[t] = np_
                sc += VAL[np_][t]
                self.pawns -= 1
                self.npm += MAT[promo]
            else:
                b[t] = piece
                sc += VAL[piece][t]
        else:
            b[t] = piece
            if ap == 6:
                self.king_sq[0 if side == 1 else 1] = t
                if t - f == 2:                                                  # تبييت قصير
                    rook = b[f + 3]
                    b[f + 1] = rook
                    b[f + 3] = 0
                    sc += VAL[rook][f + 1] - VAL[rook][f + 3]
                elif t - f == -2:                                               # تبييت طويل
                    rook = b[f - 4]
                    b[f - 1] = rook
                    b[f - 4] = 0
                    sc += VAL[rook][f - 1] - VAL[rook][f - 4]
            else:
                sc += VAL[piece][t]
        self.score = sc
        self.castle &= CASTLE_MASK[f] & CASTLE_MASK[t]
        self.ep = new_ep
        self.halfmove = 0 if reset else self.halfmove + 1
        self.side = -side

    def unmake(self):
        f, t, piece, cap, castle, ep, halfmove, score, npm, pawns = self.stack.pop()
        b = self.sq
        side = self.side = -self.side
        b[f] = piece
        b[t] = cap
        ap = piece if piece > 0 else -piece
        if ap == 1:
            d = t - f
            if cap == 0 and (d == 9 or d == 11 or d == -9 or d == -11):
                b[t - 10 * side] = -side
        elif ap == 6:
            self.king_sq[0 if side == 1 else 1] = f
            if t - f == 2:
                b[f + 3] = b[f + 1]
                b[f + 1] = 0
            elif t - f == -2:
                b[f - 4] = b[f - 1]
                b[f - 1] = 0
        self.castle = castle
        self.ep = ep
        self.halfmove = halfmove
        self.score = score
        self.npm = npm
        self.pawns = pawns

    def legal_moves(self):
        """كل النقلات القانونية: قائمة (من، إلى، ترقية)."""
        out = []
        for _, f, t, promo in self.gen_moves():
            self.make(f, t, promo)
            if not self.illegal():
                out.append((f, t, promo))
            self.unmake()
        return out

    def illegal(self):
        """بعد make(): هل ترك اللاعب الذي نفّذ النقلة ملكه في كش؟"""
        mover = -self.side
        return self.is_attacked(self.king_sq[0 if mover == 1 else 1], self.side)

    # ---------------------------------------------------------------- التقييم
    def evaluate(self):
        """قيمة الوضعية من وجهة نظر من عليه الدور (موجب = جيد له)."""
        if self.pawns == 0 and self.npm <= 330:
            return 0                                   # ملك ضد ملك (+ قطعة صغيرة): تعادل
        s = self.score
        wk, bk = self.king_sq
        if self.npm <= ENDGAME_NPM:
            s += KEND_V[wk] + KEND_B[bk]
            if s > 300 or s < -300:                    # نهاية لعبة وفارق مادي: اضغط على ملك الخصم نحو الحافة
                strong, weak = (wk, bk) if s > 0 else (bk, wk)
                dist = abs(strong % 10 - weak % 10) + abs(strong // 10 - weak // 10)
                bonus = 10 * CENTER_DIST[weak] + 4 * (14 - dist)
                s += bonus if s > 0 else -bonus
        else:
            s += KMID_V[wk] + KMID_B[bk]
        return s if self.side == 1 else -s

    def key(self):
        """مفتاح الوضعية لاكتشاف التكرار (القطع + الدور + التبييت + المرور إن كان ممكناً فعلاً)."""
        ep = 0
        if self.ep:
            side, b = self.side, self.sq
            if b[self.ep - 10 * side - 1] == side or b[self.ep - 10 * side + 1] == side:
                ep = self.ep
        return (tuple(self.sq), self.side, self.castle, ep)


# ============================================================================
#  البحث
# ============================================================================
class Searcher:
    def __init__(self, board, deadline, seen):
        self.board = board
        self.deadline = deadline
        self.nodes = 0
        self.seen = seen            # عدد مرات ظهور الوضعيات في هذه اللعبة (لتفادي التعادل بالتكرار)

    def _tick(self):
        self.nodes += 1
        if self.deadline and (self.nodes & 2047) == 0 and time.time() > self.deadline:
            raise Timeout()

    def quiesce(self, alpha, beta, ply):
        """بحث الهدوء: نكمل التبادلات (الأكل) فقط حتى تهدأ الوضعية."""
        self._tick()
        board = self.board
        stand = board.evaluate()
        if stand >= beta:
            return stand
        if stand > alpha:
            alpha = stand
        if ply >= MAX_PLY:
            return stand
        moves = board.gen_moves(True)
        moves.sort(reverse=True)
        for _, f, t, promo in moves:
            board.make(f, t, promo)
            if board.illegal():
                board.unmake()
                continue
            score = -self.quiesce(-beta, -alpha, ply + 1)
            board.unmake()
            if score >= beta:
                return score
            if score > alpha:
                alpha = score
        return alpha

    def negamax(self, depth, alpha, beta, ply):
        """DFS بعمق محدود (depth) مع قص ألفا-بيتا. القيمة من وجهة نظر من عليه الدور."""
        self._tick()
        board = self.board
        if board.halfmove >= 100:
            return 0
        in_check = board.in_check()
        if depth <= 0 and not in_check:
            return self.quiesce(alpha, beta, ply)
        if ply >= MAX_PLY:
            return board.evaluate()
        best = -INF
        legal = 0
        moves = board.gen_moves()
        moves.sort(reverse=True)
        for _, f, t, promo in moves:
            board.make(f, t, promo)
            if board.illegal():
                board.unmake()
                continue
            legal += 1
            score = -self.negamax(depth - 1, -beta, -alpha, ply + 1)
            board.unmake()
            if score > best:
                best = score
                if score > alpha:
                    alpha = score
                    if alpha >= beta:
                        break
        if legal == 0:
            return -MATE + ply if in_check else 0       # كش مات (الأقرب أسوأ لمن عليه) أو تعادل
        return best

    def root(self, depth, first):
        board = self.board
        moves = board.gen_moves()
        moves.sort(reverse=True)
        if first is not None:                            # نقلة العمق السابق أولاً
            moves.sort(key=lambda m: (m[1], m[2], m[3]) != first)
        alpha, best, best_move = -INF, -INF, None
        for _, f, t, promo in moves:
            board.make(f, t, promo)
            if board.illegal():
                board.unmake()
                continue
            if self.seen.get(board.key(), 0) >= 2:       # هذه النقلة تُنتج الوضعية للمرة الثالثة = تعادل
                score = 0
            else:
                score = -self.negamax(depth - 1, -INF, -alpha, 1)
            board.unmake()
            if score > best:
                best, best_move = score, (f, t, promo)
                if score > alpha:
                    alpha = score
        return (best, best_move) if best_move else None


# ============================================================================
#  كتاب الافتتاحيات
#  كل سطر = خط نظري معروف (نقلات UCI من الوضعية الابتدائية). يُبنى الكتاب عند التشغيل بتنفيذ كل نقلة
#  على الرقعة والتأكد من قانونيتها، ويُفهرس بالوضعية (وليس بترتيب النقلات) فتعمل التبديلات (transpositions).
#  يُختار عشوائياً بين النقلات المتاحة في الوضعية، والخط الذي يتكرر في أكثر من سطر يزداد احتمال اختياره.
# ============================================================================
BOOK_LINES = [
    # ---- 1.e4 e5 (الألعاب المفتوحة)
    "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7 f1e1 b7b5 a4b3 d7d6",   # روي لوبيز (المغلقة)
    "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6 d2d3 d7d6",                       # إيطالية (جيوكو بيانو)
    "e2e4 e7e5 g1f3 b8c6 d2d4 e5d4 f3d4 f8c5",                                 # سكوتش
    # ---- 1.e4 c5 (صقلية)
    "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6",                       # ناجدورف
    "e2e4 c7c5 g1f3 b8c6 d2d4 c5d4 f3d4 g8f6 b1c3 e7e5",                       # سفيشنيكوف
    "e2e4 c7c5 g1f3 e7e6 d2d4 c5d4 f3d4 a7a6",                                 # كان / باولسن
    # ---- 1.e4 e6 / c6 / d5
    "e2e4 e7e6 d2d4 d7d5 b1c3 g8f6 c1g5 f8e7 e4e5 f6d7",                       # فرنسية كلاسيكية
    "e2e4 e7e6 d2d4 d7d5 e4e5 c7c5 c2c3 b8c6",                                 # فرنسية (التقدم)
    "e2e4 c7c6 d2d4 d7d5 b1c3 d5e4 c3e4 c8f5 e4g3 f5g6 h2h4 h7h6",             # كارو كان
    "e2e4 d7d5 e4d5 d8d5 b1c3 d5a5 d2d4 g8f6",                                 # سكندنافية
    # ---- 1.d4 d5 (رفض / قبول مناورة الوزير، السلافية)
    "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6 c1g5 f8e7 e2e3 e8g8",                       # QGD
    "d2d4 d7d5 c2c4 c7c6 g1f3 g8f6 b1c3 d5c4 a2a4 c8f5",                       # السلافية
    "d2d4 d7d5 c2c4 d5c4 g1f3 g8f6 e2e3 e7e6 f1c4 c7c5",                       # QGA
    # ---- 1.d4 Nf6 (عندما يلعبها الخصم)
    "d2d4 g8f6 c2c4 e7e6 b1c3 f8b4 e2e3 e8g8",                                 # نيمزوفيتش
    "d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4 d7d6",                                 # هندية الملك
    # ---- الإنجليزية وأخرى (للردّ على افتتاحيات الخصم)
    "c2c4 e7e5 b1c3 g8f6 g1f3 b8c6 g2g3 d7d5",
    "c2c4 c7c5 b1c3 b8c6 g2g3 g7g6 f1g2 f8g7",
    "g1f3 d7d5 g2g3 g8f6 f1g2 e7e6 e1g1 f8e7",
    "b2b3 e7e5 c1b2 b8c6 e2e3 d7d5",
    "f2f4 d7d5 g1f3 g7g6",
    "e2e3 e7e5 d2d4 e5d4 e3d4 d7d5",
    "g2g3 e7e5 f1g2 d7d5 d2d3 g8f6",
]
# أول نقلة للحاسوب عندما يلعب بالأبيض (بيادق فقط، والتكرار = وزن أكبر)
FIRST_MOVES_WHITE = ["e2e4", "e2e4", "e2e4", "d2d4", "d2d4", "c2c4"]
# ردّ الحاسوب (بالأسود) على أول نقلة غير موجودة في الكتاب: بيدق مركزي
GENERIC_FIRST_REPLY = ["e7e5", "d7d5", "c7c5"]


def _parse(mv):
    return (name_to_sq(mv[:2]), name_to_sq(mv[2:4]), 0)


def _build_book():
    book = {}
    for line in BOOK_LINES:
        b = Board(START_FEN)
        for mv in line.split():
            m = _parse(mv)
            if m not in b.legal_moves():                  # حماية: لا نُدخل نقلة غير قانونية أبداً
                print(f"[search] book line skipped at {mv}: {line}", file=sys.stderr, flush=True)
                break
            book.setdefault(b.key(), []).append(m)
            b.make(*m)
    return book


BOOK = _build_book()
_START_KEY = Board(START_FEN).key()


def book_move(board):
    """نقلة من الكتاب للوضعية الحالية، أو None إن لم تكن فيه (فيتولى البحث)."""
    if not USE_BOOK:
        return None
    if board.key() == _START_KEY:
        cands = [_parse(m) for m in FIRST_MOVES_WHITE]
    else:
        cands = list(BOOK.get(board.key(), []))
        if BOOK_AVOID_KNIGHT_START and board.fullmove == 1:
            cands = [m for m in cands if abs(board.sq[m[0]]) != 2]            # بلا أحصنة في أول نقلة
            if not cands and board.side == -1:
                cands = [_parse(m) for m in GENERIC_FIRST_REPLY]
    legal = set(board.legal_moves())
    cands = [m for m in cands if m in legal]
    return random.choice(cands) if cands else None


# ============================================================================
#  الواجهة مع Java
# ============================================================================
_seen = {}
_last_fullmove = [0]


PROMO_LETTER = {5: "q", 4: "r", 3: "b", 2: "n"}


def uci(m):
    f, t, promo = m
    return sq_name(f) + sq_name(t) + PROMO_LETTER.get(promo, "")


def log(*args):
    print("[search]", *args, file=sys.stderr, flush=True)


def choose_move(fen):
    """يستقبل FEN ويُرجع أفضل نقلة بصيغة UCI."""
    board = Board(fen)
    if board.fullmove < _last_fullmove[0]:               # بدأت لعبة جديدة
        _seen.clear()
    _last_fullmove[0] = board.fullmove
    _seen[board.key()] = _seen.get(board.key(), 0) + 1

    bm = book_move(board)                                # كتاب الافتتاحيات أولاً
    if bm:
        board.make(*bm)
        _seen[board.key()] = _seen.get(board.key(), 0) + 1
        log(f"book move {uci(bm)}")
        return uci(bm)

    start = time.time()
    deadline = None if FIXED_DEPTH else start + TIME_LIMIT_SECONDS
    limit = FIXED_DEPTH or MAX_DEPTH
    best = None
    total_nodes = 0
    for depth in range(1, limit + 1):
        searcher = Searcher(Board(fen), deadline if depth > 1 else None, _seen)   # العمق 1 يكتمل دائماً
        try:
            result = searcher.root(depth, best[1] if best else None)
        except Timeout:
            total_nodes += searcher.nodes
            break
        total_nodes += searcher.nodes
        if result is None:
            return "0000"                                # لا نقلات قانونية (Java لا تسأل في هذه الحالة)
        best = (result[0], result[1], depth)
        elapsed = time.time() - start
        log(f"depth={depth} score={result[0]:+d} best={uci(result[1])} nodes={total_nodes} time={elapsed:.2f}s")
        if abs(result[0]) > MATE - 1000:                 # وجدنا كش مات إجباري
            break

    score, move, depth = best
    board.make(*move)
    _seen[board.key()] = _seen.get(board.key(), 0) + 1   # الوضعية بعد نقلتنا
    log(f"-> {uci(move)}  (searched to depth {depth}, {total_nodes} nodes, {time.time() - start:.2f}s)")
    return uci(move)


# ============================================================================
#  اختبار ذاتي:  python search.py
# ============================================================================
def perft(board, depth):
    if depth == 0:
        return 1
    n = 0
    for _, f, t, promo in board.gen_moves():
        board.make(f, t, promo)
        if not board.illegal():
            n += perft(board, depth - 1)
        board.unmake()
    return n


if __name__ == "__main__":
    cases = [
        ("start", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", [20, 400, 8902]),
        ("kiwipete", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", [48, 2039, 97862]),
        ("endgame", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", [14, 191, 2812]),
        ("promo+castle", "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", [6, 264, 9467]),
        ("position5", "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", [44, 1486, 62379]),
    ]
    ok = True
    print("== اختبار القواعد (perft) ==")
    for name, fen, expected in cases:
        for d, exp in enumerate(expected, 1):
            got = perft(Board(fen), d)
            ok &= got == exp
            print(f"{name:13} depth {d}: {got:7d}  {'OK' if got == exp else 'WRONG (expected %d)' % exp}")
    print("كل الاختبارات نجحت" if ok else "يوجد خطأ!")
