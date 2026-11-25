#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <random>
#include <sstream>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

namespace
{

    constexpr int MATE = 100000;
    constexpr int MATE_BOUND = MATE - 2000;
    constexpr int INF = 1000000000;
    constexpr int MAX_PLY = 64;
    constexpr int ENDGAME_NPM = 2600;
    constexpr int OFF = 99;

    constexpr int MAX_DEPTH = 14;
    constexpr double TIME_LIMIT_SECONDS = 0.0;
    constexpr int FIXED_DEPTH = 12; // عمق ثابت (11 أو 12 يعطي مستوى ثابتاً قوياً جداً) 
    constexpr bool USE_BOOK = true;
    constexpr bool BOOK_AVOID_KNIGHT_START = true;

    constexpr std::size_t TT_SIZE = 1ULL << 24; // ~16.7M مدخل (~268 MB)
    constexpr std::size_t TT_MASK = TT_SIZE - 1;

    constexpr const char *START_FEN =
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    constexpr std::array<int, 64> SQUARES = {
        21, 22, 23, 24, 25, 26, 27, 28,
        31, 32, 33, 34, 35, 36, 37, 38,
        41, 42, 43, 44, 45, 46, 47, 48,
        51, 52, 53, 54, 55, 56, 57, 58,
        61, 62, 63, 64, 65, 66, 67, 68,
        71, 72, 73, 74, 75, 76, 77, 78,
        81, 82, 83, 84, 85, 86, 87, 88,
        91, 92, 93, 94, 95, 96, 97, 98};

    constexpr std::array<int, 8> KNIGHT_D = {8, 12, 19, 21, -8, -12, -19, -21};
    constexpr std::array<int, 8> KING_D = {1, -1, 10, -10, 9, -9, 11, -11};
    constexpr std::array<int, 4> ROOK_D = {1, -1, 10, -10};
    constexpr std::array<int, 4> BISHOP_D = {9, -9, 11, -11};
    constexpr std::array<int, 7> MAT = {0, 100, 320, 330, 500, 900, 0};

    constexpr std::array<int, 64> PST_PAWN = {
        0, 0, 0, 0, 0, 0, 0, 0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5, 5, 10, 25, 25, 10, 5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, -5, -10, 0, 0, -10, -5, 5,
        5, 10, 10, -20, -20, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0};

    constexpr std::array<int, 64> PST_KNIGHT = {
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50};

    constexpr std::array<int, 64> PST_BISHOP = {
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20};

    constexpr std::array<int, 64> PST_ROOK = {
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, 10, 10, 10, 10, 5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        0, 0, 0, 5, 5, 0, 0, 0};

    constexpr std::array<int, 64> PST_QUEEN = {
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -5, 0, 5, 5, 5, 5, 0, -5,
        0, 0, 5, 5, 5, 5, 0, -5,
        -10, 5, 5, 5, 5, 5, 0, -10,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20};

    constexpr std::array<int, 64> KING_MID = {
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        20, 20, 0, 0, 0, 0, 20, 20,
        20, 30, 10, 0, 0, 10, 30, 20};

    constexpr std::array<int, 64> KING_END = {
        -50, -40, -30, -20, -20, -30, -40, -50,
        -30, -20, -10, 0, 0, -10, -20, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -30, 0, 0, 0, 0, -30, -30,
        -50, -30, -30, -30, -30, -30, -30, -50};

    struct Tables
    {
        std::array<int, 120> castle_mask{};
        std::array<std::array<int, 120>, 13> val{};
        std::array<int, 120> kmid_v{};
        std::array<int, 120> kend_v{};
        std::array<int, 120> kmid_b{};
        std::array<int, 120> kend_b{};
        std::array<int, 120> center_dist{};

        std::array<std::array<std::uint64_t, 120>, 13> z_piece{};
        std::array<std::uint64_t, 16> z_castle{};
        std::array<std::uint64_t, 120> z_ep{};
        std::uint64_t z_side = 0;

        Tables()
        {
            castle_mask.fill(15);
            castle_mask[21] = 15 & ~2;
            castle_mask[25] = 15 & ~3;
            castle_mask[28] = 15 & ~1;
            castle_mask[91] = 15 & ~8;
            castle_mask[95] = 15 & ~12;
            castle_mask[98] = 15 & ~4;

            const std::array<const int *, 6> pst_ptrs = {
                nullptr, PST_PAWN.data(), PST_KNIGHT.data(),
                PST_BISHOP.data(), PST_ROOK.data(), PST_QUEEN.data()};

            for (int r = 0; r < 8; ++r)
            {
                for (int f = 0; f < 8; ++f)
                {
                    int s = 21 + f + 10 * r;
                    for (int p = 1; p <= 5; ++p)
                    {
                        int w_pst = pst_ptrs[p][(7 - r) * 8 + f];
                        int b_pst = pst_ptrs[p][r * 8 + f];
                        val[6 + p][s] = MAT[p] + w_pst;
                        val[6 - p][s] = -(MAT[p] + b_pst);
                    }
                    kmid_v[s] = KING_MID[(7 - r) * 8 + f];
                    kend_v[s] = KING_END[(7 - r) * 8 + f];
                    kmid_b[s] = -KING_MID[r * 8 + f];
                    kend_b[s] = -KING_END[r * 8 + f];
                    center_dist[s] = std::max(3 - f, f - 4) + std::max(3 - r, r - 4);
                }
            }

            std::mt19937_64 rng(0xB0A2D3C5E7F91234ULL);
            for (int p = 0; p < 13; ++p)
            {
                for (int s = 0; s < 120; ++s)
                {
                    z_piece[p][s] = (p == 6) ? 0ULL : rng();
                }
            }
            for (int c = 0; c < 16; ++c)
                z_castle[c] = rng();
            for (int s = 0; s < 120; ++s)
                z_ep[s] = rng();
            z_side = rng();
        }
    };

    const Tables &T()
    {
        static const Tables tables;
        return tables;
    }

    enum TTFlag : std::uint8_t
    {
        TT_NONE = 0,
        TT_EXACT = 1,
        TT_LOWER = 2,
        TT_UPPER = 3
    };

    struct TTEntry
    {
        std::uint64_t key = 0;
        int score = 0;
        int depth = -1;
        TTFlag flag = TT_NONE;
        int from = 0;
        int to = 0;
        int promo = 0;
    };

    struct Move
    {
        int ord = 0;
        int from = 0;
        int to = 0;
        int promo = 0;

        bool operator>(const Move &other) const
        {
            return ord > other.ord;
        }
    };

    struct Undo
    {
        int from;
        int to;
        int piece;
        int cap;
        int castle;
        int ep;
        int halfmove;
        int score;
        int npm;
        int pawns;
        std::uint64_t hash;
    };

    struct BoardKey
    {
        std::array<int, 120> sq{};
        int side = 1;
        int castle = 0;
        int ep = 0;

        bool operator==(const BoardKey &o) const
        {
            return side == o.side && castle == o.castle && ep == o.ep && sq == o.sq;
        }
    };

    struct BoardKeyHash
    {
        std::size_t operator()(const BoardKey &k) const noexcept
        {
            std::uint64_t h = 14695981039346656037ULL;
            auto mix = [&](std::uint64_t v)
            {
                h ^= v;
                h *= 1099511628211ULL;
            };
            for (int s : SQUARES)
            {
                mix(static_cast<std::uint64_t>(k.sq[s] + 8));
            }
            mix(static_cast<std::uint64_t>(k.side + 2));
            mix(static_cast<std::uint64_t>(k.castle));
            mix(static_cast<std::uint64_t>(k.ep));
            return static_cast<std::size_t>(h);
        }
    };

    inline int piece_kind(int piece)
    {
        return piece >= 0 ? piece : -piece;
    }

    inline int name_to_sq(const std::string &n)
    {
        int f = n[0] - 'a';
        int r = (n[1] - '1');
        return 21 + f + 10 * r;
    }

    inline std::string sq_name(int s)
    {
        std::string out;
        out.push_back(static_cast<char>('a' + (s % 10 - 1)));
        out.push_back(static_cast<char>('1' + (s / 10 - 2)));
        return out;
    }

    inline int score_to_tt(int score, int ply)
    {
        if (score > MATE_BOUND)
            return score + ply;
        if (score < -MATE_BOUND)
            return score - ply;
        return score;
    }

    inline int score_from_tt(int score, int ply)
    {
        if (score > MATE_BOUND)
            return score - ply;
        if (score < -MATE_BOUND)
            return score + ply;
        return score;
    }

    class Board
    {
    public:
        std::array<int, 120> sq{};
        std::array<int, 2> king_sq{0, 0};
        std::vector<Undo> stack;
        int score = 0;
        int npm = 0;
        int pawns = 0;
        int side = 1;
        int castle = 0;
        int ep = 0;
        int halfmove = 0;
        int fullmove = 1;
        std::uint64_t hash = 0;

        explicit Board(const std::string &fen)
        {
            sq.fill(OFF);
            for (int s : SQUARES)
                sq[s] = 0;

            std::istringstream iss(fen);
            std::string placement, turn, castling, ep_str;
            iss >> placement >> turn >> castling >> ep_str;
            if (iss >> halfmove)
            {
            }
            else
                halfmove = 0;
            if (iss >> fullmove)
            {
            }
            else
                fullmove = 1;

            int rank = 7;
            int file = 0;
            for (char ch : placement)
            {
                if (ch == '/')
                {
                    --rank;
                    file = 0;
                    continue;
                }
                if (ch >= '1' && ch <= '8')
                {
                    file += (ch - '0');
                    continue;
                }
                char lower = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
                int kind = 0;
                switch (lower)
                {
                case 'p':
                    kind = 1;
                    break;
                case 'n':
                    kind = 2;
                    break;
                case 'b':
                    kind = 3;
                    break;
                case 'r':
                    kind = 4;
                    break;
                case 'q':
                    kind = 5;
                    break;
                case 'k':
                    kind = 6;
                    break;
                default:
                    break;
                }
                bool is_white = (ch >= 'A' && ch <= 'Z');
                int piece = is_white ? kind : -kind;
                int s = 21 + file + 10 * rank;
                sq[s] = piece;
                if (kind == 6)
                {
                    king_sq[is_white ? 0 : 1] = s;
                }
                else
                {
                    score += T().val[6 + piece][s];
                    if (kind == 1)
                        ++pawns;
                    else
                        npm += MAT[kind];
                }
                ++file;
            }

            side = (turn == "w") ? 1 : -1;
            castle = 0;
            if (castling.find('K') != std::string::npos)
                castle |= 1;
            if (castling.find('Q') != std::string::npos)
                castle |= 2;
            if (castling.find('k') != std::string::npos)
                castle |= 4;
            if (castling.find('q') != std::string::npos)
                castle |= 8;

            ep = (ep_str == "-" || ep_str.empty()) ? 0 : name_to_sq(ep_str);
            stack.reserve(128);
            hash = compute_hash();
        }

        std::uint64_t compute_hash() const
        {
            std::uint64_t h = 0ULL;
            for (int s : SQUARES)
            {
                if (sq[s] != 0)
                    h ^= T().z_piece[6 + sq[s]][s];
            }
            if (side == -1)
                h ^= T().z_side;
            h ^= T().z_castle[castle & 15];
            if (ep != 0)
                h ^= T().z_ep[ep];
            return h;
        }

        bool is_attacked(int s, int by) const
        {
            if (by == 1)
            {
                if (sq[s - 9] == 1 || sq[s - 11] == 1)
                    return true;
            }
            else
            {
                if (sq[s + 9] == -1 || sq[s + 11] == -1)
                    return true;
            }

            int n = 2 * by;
            for (int d : KNIGHT_D)
            {
                if (sq[s + d] == n)
                    return true;
            }

            int k = 6 * by;
            for (int d : KING_D)
            {
                if (sq[s + d] == k)
                    return true;
            }

            int q = 5 * by;
            int r = 4 * by;
            for (int d : ROOK_D)
            {
                int x = s + d;
                int p = sq[x];
                while (p == 0)
                {
                    x += d;
                    p = sq[x];
                }
                if (p == r || p == q)
                    return true;
            }

            int bi = 3 * by;
            for (int d : BISHOP_D)
            {
                int x = s + d;
                int p = sq[x];
                while (p == 0)
                {
                    x += d;
                    p = sq[x];
                }
                if (p == bi || p == q)
                    return true;
            }
            return false;
        }

        bool in_check() const
        {
            return is_attacked(king_sq[side == 1 ? 0 : 1], -side);
        }

        void gen_moves(std::vector<Move> &out, bool caps_only = false) const
        {
            out.clear();
            out.reserve(64);
            for (int s : SQUARES)
            {
                int p = sq[s];
                if (p * side <= 0)
                    continue;
                int ap = piece_kind(p);
                if (ap == 1)
                {
                    int d = 10 * side;
                    int t = s + d;
                    bool promo_rank = (side == 1) ? (t > 90) : (t < 29);
                    if (sq[t] == 0 && (!caps_only || promo_rank))
                    {
                        if (promo_rank)
                        {
                            for (int pr : {5, 4, 3, 2})
                            {
                                out.push_back({8000 + MAT[pr], s, t, pr});
                            }
                        }
                        else
                        {
                            out.push_back({0, s, t, 0});
                            bool start = (side == 1) ? (s > 30 && s < 39) : (s > 80 && s < 89);
                            if (start && sq[t + d] == 0)
                            {
                                out.push_back({0, s, t + d, 0});
                            }
                        }
                    }
                    for (int dd : {d - 1, d + 1})
                    {
                        int ct = s + dd;
                        int c = sq[ct];
                        if (c == OFF)
                            continue;
                        if (c * side < 0)
                        {
                            int v = 10 * MAT[piece_kind(c)] - 1 + 10000;
                            if (promo_rank)
                            {
                                for (int pr : {5, 4, 3, 2})
                                {
                                    out.push_back({v + MAT[pr], s, ct, pr});
                                }
                            }
                            else
                            {
                                out.push_back({v, s, ct, 0});
                            }
                        }
                        else if (c == 0 && ct == ep)
                        {
                            out.push_back({10000 + 999, s, ct, 0});
                        }
                    }
                }
                else if (ap == 2 || ap == 6)
                {
                    const int *dirs = (ap == 2) ? KNIGHT_D.data() : KING_D.data();
                    for (int i = 0; i < 8; ++i)
                    {
                        int t = s + dirs[i];
                        int c = sq[t];
                        if (c == OFF || c * side > 0)
                            continue;
                        if (c != 0)
                        {
                            out.push_back({10 * MAT[piece_kind(c)] - ap + 10000, s, t, 0});
                        }
                        else if (!caps_only)
                        {
                            out.push_back({0, s, t, 0});
                        }
                    }
                }
                else
                {
                    const int *dirs = (ap == 3) ? BISHOP_D.data() : (ap == 4) ? ROOK_D.data()
                                                                              : KING_D.data();
                    int count = (ap == 5) ? 8 : 4;
                    for (int i = 0; i < count; ++i)
                    {
                        int dd = dirs[i];
                        int t = s + dd;
                        int c = sq[t];
                        while (c == 0)
                        {
                            if (!caps_only)
                                out.push_back({0, s, t, 0});
                            t += dd;
                            c = sq[t];
                        }
                        if (c != OFF && c * side < 0)
                        {
                            out.push_back({10 * MAT[piece_kind(c)] - ap + 10000, s, t, 0});
                        }
                    }
                }
            }
            if (!caps_only)
            {
                gen_castling(out);
            }
        }

        void gen_castling(std::vector<Move> &out) const
        {
            if (!castle)
                return;
            int base = (side == 1) ? 21 : 91;
            int b_short = (side == 1) ? 1 : 4;
            int b_long = (side == 1) ? 2 : 8;
            if (!(castle & (b_short | b_long)) || sq[base + 4] != 6 * side)
                return;
            int foe = -side;
            if (is_attacked(base + 4, foe))
                return;

            if ((castle & b_short) && sq[base + 7] == 4 * side && sq[base + 5] == 0 && sq[base + 6] == 0 &&
                !is_attacked(base + 5, foe) && !is_attacked(base + 6, foe))
            {
                out.push_back({0, base + 4, base + 6, 0});
            }
            if ((castle & b_long) && sq[base] == 4 * side && sq[base + 1] == 0 && sq[base + 2] == 0 && sq[base + 3] == 0 &&
                !is_attacked(base + 3, foe) && !is_attacked(base + 2, foe))
            {
                out.push_back({0, base + 4, base + 2, 0});
            }
        }

        void make(int f, int t, int promo)
        {
            int piece = sq[f];
            int cap = sq[t];
            stack.push_back({f, t, piece, cap, castle, ep, halfmove, score, npm, pawns, hash});

            std::uint64_t h = hash;
            if (ep != 0)
                h ^= T().z_ep[ep];
            h ^= T().z_castle[castle & 15];
            h ^= T().z_piece[6 + piece][f];

            int sc = score - T().val[6 + piece][f];
            int ap = piece_kind(piece);
            int new_ep = 0;
            bool reset = (cap != 0);

            if (cap != 0)
            {
                h ^= T().z_piece[6 + cap][t];
                sc -= T().val[6 + cap][t];
                int ac = piece_kind(cap);
                if (ac == 1)
                    --pawns;
                else if (ac != 6)
                    npm -= MAT[ac];
            }

            sq[f] = 0;
            if (ap == 1)
            {
                reset = true;
                int d = t - f;
                if (cap == 0 && (d == 9 || d == 11 || d == -9 || d == -11))
                {
                    int cp = t - 10 * side;
                    h ^= T().z_piece[6 + sq[cp]][cp];
                    sc -= T().val[6 + sq[cp]][cp];
                    sq[cp] = 0;
                    --pawns;
                }
                else if (d == 20 || d == -20)
                {
                    new_ep = f + d / 2;
                }

                if (promo != 0)
                {
                    int np = promo * side;
                    sq[t] = np;
                    h ^= T().z_piece[6 + np][t];
                    sc += T().val[6 + np][t];
                    --pawns;
                    npm += MAT[promo];
                }
                else
                {
                    sq[t] = piece;
                    h ^= T().z_piece[6 + piece][t];
                    sc += T().val[6 + piece][t];
                }
            }
            else
            {
                sq[t] = piece;
                h ^= T().z_piece[6 + piece][t];
                if (ap == 6)
                {
                    king_sq[side == 1 ? 0 : 1] = t;
                    if (t - f == 2)
                    {
                        int rook = sq[f + 3];
                        h ^= T().z_piece[6 + rook][f + 3];
                        h ^= T().z_piece[6 + rook][f + 1];
                        sq[f + 1] = rook;
                        sq[f + 3] = 0;
                        sc += T().val[6 + rook][f + 1] - T().val[6 + rook][f + 3];
                    }
                    else if (t - f == -2)
                    {
                        int rook = sq[f - 4];
                        h ^= T().z_piece[6 + rook][f - 4];
                        h ^= T().z_piece[6 + rook][f - 1];
                        sq[f - 1] = rook;
                        sq[f - 4] = 0;
                        sc += T().val[6 + rook][f - 1] - T().val[6 + rook][f - 4];
                    }
                }
                else
                {
                    sc += T().val[6 + piece][t];
                }
            }

            score = sc;
            castle &= T().castle_mask[f] & T().castle_mask[t];
            ep = new_ep;
            halfmove = reset ? 0 : halfmove + 1;
            side = -side;

            h ^= T().z_castle[castle & 15];
            if (ep != 0)
                h ^= T().z_ep[ep];
            h ^= T().z_side;
            hash = h;
        }

        void unmake()
        {
            Undo u = stack.back();
            stack.pop_back();

            side = -side;
            sq[u.from] = u.piece;
            sq[u.to] = u.cap;
            int ap = piece_kind(u.piece);
            if (ap == 1)
            {
                int d = u.to - u.from;
                if (u.cap == 0 && (d == 9 || d == 11 || d == -9 || d == -11))
                {
                    sq[u.to - 10 * side] = -side;
                }
            }
            else if (ap == 6)
            {
                king_sq[side == 1 ? 0 : 1] = u.from;
                if (u.to - u.from == 2)
                {
                    sq[u.from + 3] = sq[u.from + 1];
                    sq[u.from + 1] = 0;
                }
                else if (u.to - u.from == -2)
                {
                    sq[u.from - 4] = sq[u.from - 1];
                    sq[u.from - 1] = 0;
                }
            }
            castle = u.castle;
            ep = u.ep;
            halfmove = u.halfmove;
            score = u.score;
            npm = u.npm;
            pawns = u.pawns;
            hash = u.hash;
        }

        void make_null()
        {
            stack.push_back({0, 0, 0, 0, castle, ep, halfmove, score, npm, pawns, hash});
            std::uint64_t h = hash;
            if (ep != 0)
                h ^= T().z_ep[ep];
            ep = 0;
            side = -side;
            h ^= T().z_side;
            hash = h;
        }

        void unmake_null()
        {
            Undo u = stack.back();
            stack.pop_back();
            side = -side;
            castle = u.castle;
            ep = u.ep;
            halfmove = u.halfmove;
            score = u.score;
            npm = u.npm;
            pawns = u.pawns;
            hash = u.hash;
        }

        bool illegal() const
        {
            int mover = -side;
            return is_attacked(king_sq[mover == 1 ? 0 : 1], side);
        }

        std::vector<std::array<int, 3>> legal_moves() const
        {
            Board copy = *this;
            copy.stack.clear();
            copy.stack.reserve(8);
            std::vector<Move> ps;
            copy.gen_moves(ps, false);
            std::vector<std::array<int, 3>> out;
            out.reserve(ps.size());
            for (const auto &m : ps)
            {
                copy.make(m.from, m.to, m.promo);
                if (!copy.illegal())
                {
                    out.push_back({m.from, m.to, m.promo});
                }
                copy.unmake();
            }
            return out;
        }

        int evaluate() const
        {
            if (pawns == 0 && npm <= 330)
                return 0;
            int s = score;
            int wk = king_sq[0];
            int bk = king_sq[1];

            int w_bishops = 0, b_bishops = 0;
            std::array<int, 8> w_pawn_file{};
            std::array<int, 8> b_pawn_file{};
            std::array<int, 8> w_max_rank{};
            std::array<int, 8> b_min_rank{};
            w_min_rank_init(b_min_rank);

            for (int f = 0; f < 8; ++f)
            {
                for (int r = 0; r < 8; ++r)
                {
                    int p = sq[21 + f + 10 * r];
                    if (p == 1)
                    {
                        ++w_pawn_file[f];
                        if (r > w_max_rank[f])
                            w_max_rank[f] = r;
                    }
                    else if (p == -1)
                    {
                        ++b_pawn_file[f];
                        if (r < b_min_rank[f])
                            b_min_rank[f] = r;
                    }
                    else if (p == 3)
                    {
                        ++w_bishops;
                    }
                    else if (p == -3)
                    {
                        ++b_bishops;
                    }
                }
            }

            if (w_bishops >= 2)
                s += 30;
            if (b_bishops >= 2)
                s -= 30;

            for (int f = 0; f < 8; ++f)
            {
                if (w_pawn_file[f] > 0)
                {
                    bool no_left = (f == 0 || w_pawn_file[f - 1] == 0);
                    bool no_right = (f == 7 || w_pawn_file[f + 1] == 0);
                    if (no_left && no_right)
                        s -= 15;
                    if (b_pawn_file[f] == 0)
                    {
                        bool passed = true;
                        for (int df = -1; df <= 1; ++df)
                        {
                            int nf = f + df;
                            if (nf >= 0 && nf < 8 && b_pawn_file[nf] > 0 && b_min_rank[nf] > w_max_rank[f])
                            {
                                passed = false;
                                break;
                            }
                        }
                        if (passed)
                            s += 12 + w_max_rank[f] * 6;
                    }
                }
                if (b_pawn_file[f] > 0)
                {
                    bool no_left = (f == 0 || b_pawn_file[f - 1] == 0);
                    bool no_right = (f == 7 || b_pawn_file[f + 1] == 0);
                    if (no_left && no_right)
                        s += 15;
                    if (w_pawn_file[f] == 0)
                    {
                        bool passed = true;
                        for (int df = -1; df <= 1; ++df)
                        {
                            int nf = f + df;
                            if (nf >= 0 && nf < 8 && w_pawn_file[nf] > 0 && w_max_rank[nf] < b_min_rank[f])
                            {
                                passed = false;
                                break;
                            }
                        }
                        if (passed)
                            s -= 12 + (7 - b_min_rank[f]) * 6;
                    }
                }
            }

            for (int r = 0; r < 8; ++r)
            {
                for (int f = 0; f < 8; ++f)
                {
                    int p = sq[21 + f + 10 * r];
                    if (p == 4)
                    {
                        if (w_pawn_file[f] == 0 && b_pawn_file[f] == 0)
                            s += 20;
                        else if (w_pawn_file[f] == 0)
                            s += 10;
                    }
                    else if (p == -4)
                    {
                        if (w_pawn_file[f] == 0 && b_pawn_file[f] == 0)
                            s -= 20;
                        else if (b_pawn_file[f] == 0)
                            s -= 10;
                    }
                }
            }

            if (npm <= ENDGAME_NPM)
            {
                s += T().kend_v[wk] + T().kend_b[bk];
                if (s > 300 || s < -300)
                {
                    int strong = (s > 0) ? wk : bk;
                    int weak = (s > 0) ? bk : wk;
                    int dist = std::abs(strong % 10 - weak % 10) + std::abs(strong / 10 - weak / 10);
                    int bonus = 10 * T().center_dist[weak] + 4 * (14 - dist);
                    s += (s > 0) ? bonus : -bonus;
                }
            }
            else
            {
                s += T().kmid_v[wk] + T().kmid_b[bk];
            }
            return (side == 1) ? s : -s;
        }

        BoardKey key() const
        {
            BoardKey k;
            k.sq = sq;
            k.side = side;
            k.castle = castle;
            k.ep = 0;
            if (ep != 0)
            {
                int idx1 = ep - 10 * side - 1;
                int idx2 = ep - 10 * side + 1;
                if (sq[idx1] == side || sq[idx2] == side)
                {
                    k.ep = ep;
                }
            }
            return k;
        }

    private:
        static void w_min_rank_init(std::array<int, 8> &arr)
        {
            arr.fill(8);
        }
    };

    struct TimeoutEx
    {
    };

    class TranspositionTable
    {
    public:
        std::vector<TTEntry> table;

        TranspositionTable() : table(TT_SIZE) {}

        void clear()
        {
            std::fill(table.begin(), table.end(), TTEntry{});
        }

        bool probe(std::uint64_t hash, TTEntry &out) const
        {
            const TTEntry &e = table[static_cast<std::size_t>(hash) & TT_MASK];
            if (e.flag != TT_NONE && e.key == hash)
            {
                out = e;
                return true;
            }
            return false;
        }

        void store(std::uint64_t hash, int depth, int score, TTFlag flag, const std::array<int, 3> &mv)
        {
            TTEntry &e = table[static_cast<std::size_t>(hash) & TT_MASK];
            if (e.key != hash || depth >= e.depth - 1)
            {
                e.key = hash;
                e.depth = depth;
                e.score = score;
                e.flag = flag;
                e.from = mv[0];
                e.to = mv[1];
                e.promo = mv[2];
            }
        }
    };

    class Searcher
    {
    public:
        using SeenMap = std::unordered_map<BoardKey, int, BoardKeyHash>;

        Board &board;
        TranspositionTable &tt;
        bool has_deadline;
        std::chrono::steady_clock::time_point deadline;
        const SeenMap &seen;
        std::uint64_t nodes = 0;

        std::array<std::array<std::array<int, 3>, 2>, MAX_PLY + 2> killers{};
        std::array<std::array<int, 120>, 120> history{};

        Searcher(Board &b, TranspositionTable &ttable, bool timed,
                 std::chrono::steady_clock::time_point dl, const SeenMap &s)
            : board(b), tt(ttable), has_deadline(timed), deadline(dl), seen(s)
        {
            for (auto &ply_k : killers)
            {
                for (auto &k : ply_k)
                    k = {0, 0, 0};
            }
            for (auto &row : history)
                row.fill(0);
        }

        void tick()
        {
            ++nodes;
            if (has_deadline && (nodes & 4095ULL) == 0ULL)
            {
                if (std::chrono::steady_clock::now() > deadline)
                {
                    throw TimeoutEx{};
                }
            }
        }

        void order_moves(std::vector<Move> &moves, int ply, const TTEntry *tt_hit) const
        {
            for (auto &m : moves)
            {
                if (tt_hit && m.from == tt_hit->from && m.to == tt_hit->to && m.promo == tt_hit->promo)
                {
                    m.ord = 2000000;
                }
                else if (m.ord == 0)
                {
                    if (ply < MAX_PLY)
                    {
                        const auto &k0 = killers[ply][0];
                        const auto &k1 = killers[ply][1];
                        if (m.from == k0[0] && m.to == k0[1] && m.promo == k0[2])
                        {
                            m.ord = 9000;
                        }
                        else if (m.from == k1[0] && m.to == k1[1] && m.promo == k1[2])
                        {
                            m.ord = 8500;
                        }
                        else
                        {
                            m.ord = history[m.from][m.to];
                        }
                    }
                }
            }
            std::sort(moves.begin(), moves.end(), std::greater<Move>());
        }

        void record_killer(int ply, int from, int to, int promo)
        {
            if (ply >= MAX_PLY)
                return;
            std::array<int, 3> mv{from, to, promo};
            if (killers[ply][0] != mv)
            {
                killers[ply][1] = killers[ply][0];
                killers[ply][0] = mv;
            }
        }

        int quiesce(int alpha, int beta, int ply)
        {
            tick();
            int stand = board.evaluate();
            if (stand >= beta)
                return stand;
            if (stand > alpha)
                alpha = stand;
            if (ply >= MAX_PLY)
                return stand;

            std::vector<Move> moves;
            board.gen_moves(moves, true);
            std::sort(moves.begin(), moves.end(), std::greater<Move>());

            for (const auto &m : moves)
            {
                board.make(m.from, m.to, m.promo);
                if (board.illegal())
                {
                    board.unmake();
                    continue;
                }
                int s = -quiesce(-beta, -alpha, ply + 1);
                board.unmake();
                if (s >= beta)
                    return s;
                if (s > alpha)
                    alpha = s;
            }
            return alpha;
        }

        int negamax(int depth, int alpha, int beta, int ply, bool allow_null = true)
        {
            tick();
            if (board.halfmove >= 100)
                return 0;
            if (ply >= MAX_PLY)
                return board.evaluate();

            int orig_alpha = alpha;
            TTEntry tt_hit{};
            bool has_tt = tt.probe(board.hash, tt_hit);
            std::array<int, 3> tt_move{0, 0, 0};
            if (has_tt)
            {
                tt_move = {tt_hit.from, tt_hit.to, tt_hit.promo};
                if (ply > 0 && tt_hit.depth >= depth)
                {
                    int tt_sc = score_from_tt(tt_hit.score, ply);
                    if (tt_hit.flag == TT_EXACT)
                        return tt_sc;
                    if (tt_hit.flag == TT_LOWER && tt_sc >= beta)
                        return tt_sc;
                    if (tt_hit.flag == TT_UPPER && tt_sc <= alpha)
                        return tt_sc;
                }
            }

            bool in_check = board.in_check();
            if (depth <= 0 && !in_check)
            {
                return quiesce(alpha, beta, ply);
            }
            if (depth <= 0)
                depth = 1;

            if (allow_null && !in_check && ply > 0 && depth >= 3 && board.npm > 500)
            {
                board.make_null();
                int r = (depth > 6) ? 3 : 2;
                int null_score = -negamax(depth - 1 - r, -beta, -beta + 1, ply + 1, false);
                board.unmake_null();
                if (null_score >= beta)
                {
                    if (null_score > MATE_BOUND)
                        return beta;
                    return null_score;
                }
            }

            int best = -INF;
            std::array<int, 3> best_move{0, 0, 0};
            int legal = 0;

            std::vector<Move> moves;
            board.gen_moves(moves, false);
            order_moves(moves, ply, has_tt ? &tt_hit : nullptr);

            for (const auto &m : moves)
            {
                bool is_cap = (board.sq[m.to] != 0) || (m.ord >= 10000);
                board.make(m.from, m.to, m.promo);
                if (board.illegal())
                {
                    board.unmake();
                    continue;
                }
                ++legal;
                bool gives_check = board.in_check();

                int s = 0;
                int next_depth = depth - 1 + (gives_check ? 1 : 0);

                if (legal == 1)
                {
                    s = -negamax(next_depth, -beta, -alpha, ply + 1, true);
                }
                else
                {
                    bool can_reduce = (depth >= 3 && legal > 3 && !is_cap && m.promo == 0 && !in_check && !gives_check);
                    if (can_reduce)
                    {
                        int red = (legal > 8) ? 2 : 1;
                        s = -negamax(std::max(1, next_depth - red), -alpha - 1, -alpha, ply + 1, true);
                        if (s > alpha)
                        {
                            s = -negamax(next_depth, -alpha - 1, -alpha, ply + 1, true);
                        }
                    }
                    else
                    {
                        s = -negamax(next_depth, -alpha - 1, -alpha, ply + 1, true);
                    }
                    if (s > alpha && s < beta)
                    {
                        s = -negamax(next_depth, -beta, -alpha, ply + 1, true);
                    }
                }
                board.unmake();

                if (s > best)
                {
                    best = s;
                    best_move = {m.from, m.to, m.promo};
                    if (s > alpha)
                    {
                        alpha = s;
                        if (!is_cap && m.promo == 0)
                        {
                            record_killer(ply, m.from, m.to, m.promo);
                            int &hval = history[m.from][m.to];
                            hval = std::min(8000, hval + depth * depth * 32);
                        }
                        if (alpha >= beta)
                            break;
                    }
                }
            }

            if (legal == 0)
            {
                return in_check ? (-MATE + ply) : 0;
            }

            TTFlag flag = TT_EXACT;
            if (best <= orig_alpha)
                flag = TT_UPPER;
            else if (best >= beta)
                flag = TT_LOWER;
            tt.store(board.hash, depth, score_to_tt(best, ply), flag, best_move);
            return best;
        }

        bool root(int depth, bool has_first, const std::array<int, 3> &first,
                  int window_alpha, int window_beta,
                  int &out_score, std::array<int, 3> &out_move)
        {
            std::vector<Move> moves;
            board.gen_moves(moves, false);

            TTEntry tt_hit{};
            bool has_tt = tt.probe(board.hash, tt_hit);
            order_moves(moves, 0, has_tt ? &tt_hit : nullptr);

            if (has_first)
            {
                std::stable_sort(moves.begin(), moves.end(), [&](const Move &a, const Move &b)
                                 {
                bool a_first = (a.from == first[0] && a.to == first[1] && a.promo == first[2]);
                bool b_first = (b.from == first[0] && b.to == first[1] && b.promo == first[2]);
                return a_first && !b_first; });
            }

            int alpha = window_alpha;
            int beta = window_beta;
            int best = -INF;
            bool found = false;
            std::array<int, 3> best_move{0, 0, 0};
            int legal_idx = 0;

            for (const auto &m : moves)
            {
                board.make(m.from, m.to, m.promo);
                if (board.illegal())
                {
                    board.unmake();
                    continue;
                }
                ++legal_idx;
                int s = 0;
                auto it = seen.find(board.key());
                if (it != seen.end() && it->second >= 2)
                {
                    s = 0;
                }
                else if (legal_idx == 1)
                {
                    s = -negamax(depth - 1, -beta, -alpha, 1, true);
                }
                else
                {
                    s = -negamax(depth - 1, -alpha - 1, -alpha, 1, true);
                    if (s > alpha && s < beta)
                    {
                        s = -negamax(depth - 1, -beta, -alpha, 1, true);
                    }
                }
                board.unmake();

                if (!found || s > best)
                {
                    found = true;
                    best = s;
                    best_move = {m.from, m.to, m.promo};
                    if (s > alpha)
                        alpha = s;
                }
            }

            if (!found)
                return false;
            out_score = best;
            out_move = best_move;
            tt.store(board.hash, depth, score_to_tt(best, 0), TT_EXACT, best_move);
            return true;
        }
    };

    const std::vector<std::string> BOOK_LINES = {
        "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7 f1e1 b7b5 a4b3 d7d6",
        "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6 d2d3 d7d6",
        "e2e4 e7e5 g1f3 b8c6 d2d4 e5d4 f3d4 f8c5",
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3 a7a6",
        "e2e4 c7c5 g1f3 b8c6 d2d4 c5d4 f3d4 g8f6 b1c3 e7e5",
        "e2e4 c7c5 g1f3 e7e6 d2d4 c5d4 f3d4 a7a6",
        "e2e4 e7e6 d2d4 d7d5 b1c3 g8f6 c1g5 f8e7 e4e5 f6d7",
        "e2e4 e7e6 d2d4 d7d5 e4e5 c7c5 c2c3 b8c6",
        "e2e4 c7c6 d2d4 d7d5 b1c3 d5e4 c3e4 c8f5 e4g3 f5g6 h2h4 h7h6",
        "e2e4 d7d5 e4d5 d8d5 b1c3 d5a5 d2d4 g8f6",
        "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6 c1g5 f8e7 e2e3 e8g8",
        "d2d4 d7d5 c2c4 c7c6 g1f3 g8f6 b1c3 d5c4 a2a4 c8f5",
        "d2d4 d7d5 c2c4 d5c4 g1f3 g8f6 e2e3 e7e6 f1c4 c7c5",
        "d2d4 g8f6 c2c4 e7e6 b1c3 f8b4 e2e3 e8g8",
        "d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4 d7d6",
        "c2c4 e7e5 b1c3 g8f6 g1f3 b8c6 g2g3 d7d5",
        "c2c4 c7c5 b1c3 b8c6 g2g3 g7g6 f1g2 f8g7",
        "g1f3 d7d5 g2g3 g8f6 f1g2 e7e6 e1g1 f8e7",
        "b2b3 e7e5 c1b2 b8c6 e2e3 d7d5",
        "f2f4 d7d5 g1f3 g7g6",
        "e2e3 e7e5 d2d4 e5d4 e3d4 d7d5",
        "g2g3 e7e5 f1g2 d7d5 d2d3 g8f6"};

    const std::vector<std::string> FIRST_MOVES_WHITE = {"e2e4", "e2e4", "e2e4", "d2d4", "d2d4", "c2c4"};
    const std::vector<std::string> GENERIC_FIRST_REPLY = {"e7e5", "d7d5", "c7c5"};

    std::array<int, 3> parse_uci_move(const std::string &mv)
    {
        int promo = 0;
        if (mv.size() >= 5)
        {
            switch (mv[4])
            {
            case 'q':
                promo = 5;
                break;
            case 'r':
                promo = 4;
                break;
            case 'b':
                promo = 3;
                break;
            case 'n':
                promo = 2;
                break;
            default:
                break;
            }
        }
        return {name_to_sq(mv.substr(0, 2)), name_to_sq(mv.substr(2, 2)), promo};
    }

    std::string to_uci(const std::array<int, 3> &m)
    {
        std::string s = sq_name(m[0]) + sq_name(m[1]);
        switch (m[2])
        {
        case 5:
            s.push_back('q');
            break;
        case 4:
            s.push_back('r');
            break;
        case 3:
            s.push_back('b');
            break;
        case 2:
            s.push_back('n');
            break;
        default:
            break;
        }
        return s;
    }

    class OpeningBook
    {
    public:
        std::unordered_map<BoardKey, std::vector<std::array<int, 3>>, BoardKeyHash> book;
        BoardKey start_key;
        std::mt19937 rng;

        OpeningBook() : rng(std::random_device{}())
        {
            Board sb(START_FEN);
            start_key = sb.key();

            for (const auto &line : BOOK_LINES)
            {
                Board b(START_FEN);
                std::istringstream iss(line);
                std::string tok;
                while (iss >> tok)
                {
                    auto m = parse_uci_move(tok);
                    auto legal = b.legal_moves();
                    bool is_legal = std::find(legal.begin(), legal.end(), m) != legal.end();
                    if (!is_legal)
                    {
                        std::cerr << "[search] book line skipped at " << tok << ": " << line << "\n";
                        break;
                    }
                    book[b.key()].push_back(m);
                    b.make(m[0], m[1], m[2]);
                }
            }
        }

        bool pick(const Board &board, std::array<int, 3> &out_move)
        {
            if (!USE_BOOK)
                return false;
            auto legal = board.legal_moves();
            if (legal.empty())
                return false;

            std::vector<std::array<int, 3>> cands;
            BoardKey k = board.key();
            if (k == start_key)
            {
                for (const auto &s : FIRST_MOVES_WHITE)
                    cands.push_back(parse_uci_move(s));
            }
            else
            {
                auto it = book.find(k);
                if (it != book.end())
                    cands = it->second;
                if (BOOK_AVOID_KNIGHT_START && board.fullmove == 1)
                {
                    std::vector<std::array<int, 3>> filtered;
                    for (const auto &m : cands)
                    {
                        if (piece_kind(board.sq[m[0]]) != 2)
                            filtered.push_back(m);
                    }
                    cands.swap(filtered);
                    if (cands.empty() && board.side == -1)
                    {
                        for (const auto &s : GENERIC_FIRST_REPLY)
                            cands.push_back(parse_uci_move(s));
                    }
                }
            }

            std::vector<std::array<int, 3>> valid;
            for (const auto &m : cands)
            {
                if (std::find(legal.begin(), legal.end(), m) != legal.end())
                {
                    valid.push_back(m);
                }
            }
            if (valid.empty())
                return false;
            std::uniform_int_distribution<std::size_t> dist(0, valid.size() - 1);
            out_move = valid[dist(rng)];
            return true;
        }
    };

    class EngineService
    {
    public:
        OpeningBook book;
        TranspositionTable tt;
        Searcher::SeenMap seen;
        int last_fullmove = 0;

        std::string choose_move(const std::string &fen)
        {
            Board board(fen);
            if (board.fullmove < last_fullmove)
            {
                seen.clear();
            }
            last_fullmove = board.fullmove;
            seen[board.key()] += 1;

            std::array<int, 3> bm{0, 0, 0};
            if (book.pick(board, bm))
            {
                board.make(bm[0], bm[1], bm[2]);
                seen[board.key()] += 1;
                std::cerr << "[search] book move " << to_uci(bm) << "\n"
                          << std::flush;
                return to_uci(bm);
            }

            auto start_tp = std::chrono::steady_clock::now();
            bool has_deadline = (FIXED_DEPTH == 0);
            auto deadline_tp = start_tp + std::chrono::milliseconds(static_cast<long long>(TIME_LIMIT_SECONDS * 1000.0));
            int limit = (FIXED_DEPTH > 0) ? FIXED_DEPTH : MAX_DEPTH;

            bool has_best = false;
            int best_score = 0;
            std::array<int, 3> best_move{0, 0, 0};
            int best_depth = 0;
            std::uint64_t total_nodes = 0;

            for (int depth = 1; depth <= limit; ++depth)
            {
                int sc = 0;
                std::array<int, 3> mv{0, 0, 0};
                int delta = 35;
                int w_alpha = -INF;
                int w_beta = INF;

                if (has_best && depth >= 4 && std::abs(best_score) < MATE_BOUND)
                {
                    w_alpha = best_score - delta;
                    w_beta = best_score + delta;
                }

                while (true)
                {
                    Board work(fen);
                    bool timed = has_deadline && (depth > 1);
                    Searcher s(work, tt, timed, deadline_tp, seen);
                    try
                    {
                        if (!s.root(depth, has_best, best_move, w_alpha, w_beta, sc, mv))
                        {
                            total_nodes += s.nodes;
                            return "0000";
                        }
                    }
                    catch (const TimeoutEx &)
                    {
                        total_nodes += s.nodes;
                        goto done_search;
                    }
                    total_nodes += s.nodes;

                    if (w_alpha > -INF && sc <= w_alpha)
                    {
                        w_alpha = std::max(-INF, w_alpha - delta);
                        delta *= 2;
                        continue;
                    }
                    if (w_beta < INF && sc >= w_beta)
                    {
                        w_beta = std::min(INF, w_beta + delta);
                        delta *= 2;
                        continue;
                    }
                    break;
                }

                has_best = true;
                best_score = sc;
                best_move = mv;
                best_depth = depth;

                double elapsed = std::chrono::duration<double>(std::chrono::steady_clock::now() - start_tp).count();
                std::cerr << "[search] depth=" << depth << " score=" << best_score
                          << " best=" << to_uci(best_move) << " nodes=" << total_nodes
                          << " time=" << elapsed << "s\n"
                          << std::flush;

                if (std::abs(best_score) > MATE - 1000)
                {
                    break;
                }
            }

        done_search:
            if (!has_best)
                return "0000";
            board.make(best_move[0], best_move[1], best_move[2]);
            seen[board.key()] += 1;
            double total_elapsed = std::chrono::duration<double>(std::chrono::steady_clock::now() - start_tp).count();
            std::cerr << "[search] -> " << to_uci(best_move) << "  (searched to depth " << best_depth
                      << ", " << total_nodes << " nodes, " << total_elapsed << "s)\n"
                      << std::flush;
            return to_uci(best_move);
        }
    };

    std::uint64_t perft(Board &board, int depth)
    {
        if (depth == 0)
            return 1ULL;
        std::vector<Move> moves;
        board.gen_moves(moves, false);
        std::uint64_t n = 0;
        for (const auto &m : moves)
        {
            board.make(m.from, m.to, m.promo);
            if (!board.illegal())
            {
                n += perft(board, depth - 1);
            }
            board.unmake();
        }
        return n;
    }

    int run_perft_tests()
    {
        struct Case
        {
            const char *name;
            const char *fen;
            std::vector<std::uint64_t> expected;
        };
        const std::vector<Case> cases = {
            {"start", START_FEN, {20, 400, 8902, 197281}},
            {"kiwipete", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", {48, 2039, 97862}},
            {"endgame", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", {14, 191, 2812, 43238}},
            {"promo+castle", "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", {6, 264, 9467}},
            {"position5", "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", {44, 1486, 62379}}};

        bool ok = true;
        std::cout << "== اختبار القواعد (perft) ==\n";
        for (const auto &c : cases)
        {
            for (std::size_t d = 0; d < c.expected.size(); ++d)
            {
                Board b(c.fen);
                std::uint64_t got = perft(b, static_cast<int>(d) + 1);
                bool good = (got == c.expected[d]);
                ok = ok && good;
                std::cout << c.name << " depth " << (d + 1) << ": " << got
                          << (good ? " OK\n" : " WRONG (expected " + std::to_string(c.expected[d]) + ")\n");
            }
        }
        std::cout << (ok ? "كل الاختبارات نجحت\n" : "يوجد خطأ!\n");
        return ok ? 0 : 1;
    }

} // namespace

int main(int argc, char **argv)
{
    if (argc > 1 && std::string(argv[1]) == "--perft")
    {
        return run_perft_tests();
    }

    std::ios::sync_with_stdio(false);
    std::cin.tie(nullptr);

    EngineService service;
    std::cout << "READY\n"
              << std::flush;

    std::string line;
    while (std::getline(std::cin, line))
    {
        while (!line.empty() && (line.back() == '\r' || line.back() == '\n'))
        {
            line.pop_back();
        }
        if (line.empty())
            continue;
        if (line == "quit")
            break;
        std::string reply = service.choose_move(line);
        std::cout << reply << "\n"
                  << std::flush;
    }
    return 0;
}