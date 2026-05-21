package Engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class Search {

    // Unique flags for Transposition Table entries
    private static final byte EXACT = 0;
    private static final byte LOWERBOUND = 1; // Beta cutoff
    private static final byte UPPERBOUND = 2; // Alpha fail-low

    private static final int INFINITY = 300000;
    private static final int MATE_VALUE = 290000;

    // Transposition Table Entry
    private static class TTEntry {
        long zobristKey;
        int depth;
        int score;
        byte flag;
        int bestMoveFrom;
        int bestMoveTo;
    }

    // 16MB Transposition Table size (approx. 500k positions, adjustable)
    private static final int TT_SIZE = 1 << 19;
    private static final TTEntry[] transpositionTable = new TTEntry[TT_SIZE];

    // Zobrist Hashing Matrices
    private static final long[][] zobristPieces = new long[64][13]; // 6 types * 2 colors + empty
    private static final long zobristWhiteToMove;
    private static final long[] zobristCastling = new long[16];
    private static final long[] zobristEnPassant = new long[64];

    // Killer & History Heuristics for superb move ordering
    private static final int[][] killerMovesFrom = new int[100][2];
    private static final int[][] killerMovesTo = new int[100][2];
    private static final int[][][] historyHeuristic = new int[64][64][2]; // [from][to][side]

    // Time Management
    private static long startTime;
    private static long allocatedTime;
    private static boolean stopSearch;

    static {
        // Initialize Zobrist Random Arrays statically once
        Random r = new Random(42); // Fixed seed for reproducible hashes
        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 13; j++) {
                zobristPieces[i][j] = r.nextLong();
            }
            zobristEnPassant[i] = r.nextLong();
        }
        zobristWhiteToMove = r.nextLong();
        for (int i = 0; i < 16; i++) {
            zobristCastling[i] = r.nextLong();
        }
    }

    // -------------------------------------------------------------------------
    // Public Entry Point
    // -------------------------------------------------------------------------

    /**
     * Finds the absolute best move within an allocated time constraint.
     * @param board The current state of the board.
     * @param timeLimitMillis Maximum time allowed for calculations.
     * @return An int array containing [fromIndex, toIndex, promotionChoice]
     */
    public static int[] findBestMove(Board board, long timeLimitMillis) {
        startTime = System.currentTimeMillis();
        allocatedTime = timeLimitMillis;
        stopSearch = false;

        int[] bestMove = new int[]{-1, -1, 5}; // Default Queen promotion fallback
        int lastCompleteScore = 0;

        // Clear Killer and History heuristics before starting a new tree search
        for (int i = 0; i < 100; i++) {
            killerMovesFrom[i][0] = killerMovesFrom[i][1] = -1;
            killerMovesTo[i][0] = killerMovesTo[i][1] = -1;
        }

        // --- Iterative Deepening Framework ---
        for (int depth = 1; depth <= 64; depth++) {
            int alpha = -INFINITY;
            int beta = INFINITY;

            // Optional Aspiration Windows optimization for deep searches
            int score = negamax(board, depth, 0, alpha, beta);

            if (stopSearch) {
                break; // Use move computed from previous fully completed depth layer
            }

            // Extract the Principal Variation (PV) Move from our TT
            long currentHash = computeZobristHash(board);
            int index = (int) (currentHash & (TT_SIZE - 1));
            TTEntry entry = transpositionTable[index];

            if (entry != null && entry.zobristKey == currentHash) {
                bestMove[0] = entry.bestMoveFrom;
                bestMove[1] = entry.bestMoveTo;
                lastCompleteScore = score;
            }

            // Instant win exit if mate is forced
            if (Math.abs(lastCompleteScore) > MATE_VALUE - 100) {
                break;
            }
        }

        // Automatic Pawn Promotion check if AI didn't catch it explicitly
        int absPiece = Math.abs(board.boardArray[bestMove[0]]);
        if (absPiece == 1 && (bestMove[1] / 8 == 0 || bestMove[1] / 8 == 7)) {
            bestMove[2] = 5; // Force Queen promotion for the search's final decision
        }

        return bestMove;
    }

    // -------------------------------------------------------------------------
    // Core Negamax Loop
    // -------------------------------------------------------------------------

    private static int negamax(Board board, int depth, int ply, int alpha, int beta) {
        // Immediate time boundary exit checks
        if ((ply & 15) == 0 && System.currentTimeMillis() - startTime >= allocatedTime) {
            stopSearch = true;
            return alpha;
        }

        // Handle draws by rules or insufficient material
        if (Evaluate.isInsufficientMaterial(board) || board.isStalemate()) {
            return 0;
        }

        // Read Transposition Table Cache
        long hash = computeZobristHash(board);
        int ttIndex = (int) (hash & (TT_SIZE - 1));
        TTEntry ttEntry = transpositionTable[ttIndex];

        if (ttEntry != null && ttEntry.zobristKey == hash && ttEntry.depth >= depth) {
            if (ttEntry.flag == EXACT) return ttEntry.score;
            if (ttEntry.flag == LOWERBOUND && ttEntry.score >= beta) return beta;
            if (ttEntry.flag == UPPERBOUND && ttEntry.score <= alpha) return alpha;
        }

        // Base case: switch over to tactical stabilization search
        if (depth <= 0) {
            return quiescence(board, alpha, beta);
        }

        boolean inCheck = board.isInCheck(board.whiteToMove);
        if (inCheck) depth++; // Check Extension: Don't miss vital tactical replies

        // --- Null Move Pruning (NMP) ---
        // If we skip our turn and still outperform beta, our position is overwhelmingly safe.
        if (!inCheck && depth >= 3 && hasMajorPieces(board, board.whiteToMove)) {
            board.whiteToMove = !board.whiteToMove; // Skip turn
            int nmpScore = -negamax(board, depth - 1 - 2, ply + 1, -beta, -beta + 1);
            board.whiteToMove = !board.whiteToMove; // Restore turn

            if (nmpScore >= beta) return beta;
        }

        List<Move> moveList = getOrderedMoves(board, ply, ttEntry);
        if (moveList.isEmpty()) {
            return inCheck ? (-MATE_VALUE + ply) : 0; // Checkmate or Stalemate
        }

        int origAlpha = alpha;
        int bestMoveFrom = -1;
        int bestMoveTo = -1;
        int legalMovesCount = 0;

        for (int i = 0; i < moveList.size(); i++) {
            Move move = moveList.get(i);
            Board.MoveState state = board.makeMove(move.from, move.to);

            // Handle instantaneous promotional logic side-effects inside Board.java
            if (board.pendingPromotionSquare != -1) {
                board.applyPromotion(5); // Test Queen promotion variant path
            }

            // Verify legality of the move
            if (board.isInCheck(!board.whiteToMove)) {
                board.undoMove(move.from, move.to, state);
                continue;
            }
            legalMovesCount++;

            int score;
            // --- Late Move Reductions (LMR) ---
            // Heuristically prunes deep search variations on late, non-forcing moves
            if (legalMovesCount > 4 && depth >= 3 && !move.isCapture && !inCheck) {
                score = -negamax(board, depth - 2, ply + 1, -alpha - 1, -alpha);
                if (score > alpha) { // If it was surprisingly good, re-search normally
                    score = -negamax(board, depth - 1, ply + 1, -beta, -alpha);
                }
            } else {
                score = -negamax(board, depth - 1, ply + 1, -beta, -alpha);
            }

            board.undoMove(move.from, move.to, state);

            if (stopSearch) return alpha;

            if (score >= beta) {
                // Save killer and history tables for future branch cutoffs
                if (!move.isCapture) {
                    killerMovesFrom[ply][1] = killerMovesFrom[ply][0];
                    killerMovesTo[ply][1] = killerMovesTo[ply][0];
                    killerMovesFrom[ply][0] = move.from;
                    killerMovesTo[ply][0] = move.to;

                    int side = board.whiteToMove ? 0 : 1;
                    historyHeuristic[move.from][move.to][side] += depth * depth;
                }

                storeTT(hash, depth, beta, LOWERBOUND, move.from, move.to);
                return beta; // Beta cutoff
            }

            if (score > alpha) {
                alpha = score;
                bestMoveFrom = move.from;
                bestMoveTo = move.to;
            }
        }

        if (legalMovesCount == 0) {
            return inCheck ? (-MATE_VALUE + ply) : 0;
        }

        byte flag = (alpha <= origAlpha) ? UPPERBOUND : EXACT;
        storeTT(hash, depth, alpha, flag, bestMoveFrom, bestMoveTo);

        return alpha;
    }

    // -------------------------------------------------------------------------
    // Quiescence Search (Prevents the Horizon Effect)
    // -------------------------------------------------------------------------

    private static int quiescence(Board board, int alpha, int beta) {
        if (System.currentTimeMillis() - startTime >= allocatedTime) {
            stopSearch = true;
            return alpha;
        }

        int standPat = Evaluate.evaluate(board);
        if (!board.whiteToMove) standPat = -standPat; // Negamax normalization

        if (standPat >= beta) return beta;
        if (standPat > alpha) alpha = standPat;

        List<Move> captures = getOrderedMoves(board, 0, null);

        for (Move move : captures) {
            if (!move.isCapture) continue; // Ignore passive structural shifts during Q-search

            Board.MoveState state = board.makeMove(move.from, move.to);
            if (board.pendingPromotionSquare != -1) board.applyPromotion(5);

            if (board.isInCheck(!board.whiteToMove)) {
                board.undoMove(move.from, move.to, state);
                continue;
            }

            int score = -quiescence(board, -beta, -alpha);
            board.undoMove(move.from, move.to, state);

            if (stopSearch) return alpha;

            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }

        return alpha;
    }

    // -------------------------------------------------------------------------
    // Move Ranking & Sorting Mechanism (MVV-LVA)
    // -------------------------------------------------------------------------

    private static class Move {
        int from, to, score;
        boolean isCapture;

        Move(int from, int to, int score, boolean isCapture) {
            this.from = from;
            this.to = to;
            this.score = score;
            this.isCapture = isCapture;
        }
    }

    private static List<Move> getOrderedMoves(Board board, int ply, TTEntry ttEntry) {
        List<Integer> pseudoMoves = board.generateMoves();
        List<Move> sortedMoves = new ArrayList<>(pseudoMoves.size() / 2);

        int side = board.whiteToMove ? 0 : 1;

        for (int i = 0; i < pseudoMoves.size(); i += 2) {
            // Unpack from indices dynamically if your custom step format utilizes raw pairings
            // Assuming default board.generateMoves yields lists of consecutive targets or raw integers
        }

        // Fallback robust collection mapping
        List<Integer> legalIndices = board.generateMoves();
        for (int from = 0; from < 64; from++) {
            int piece = board.boardArray[from];
            if (piece == 0 || (board.whiteToMove && piece < 0) || (!board.whiteToMove && piece > 0)) continue;

            List<Integer> targets = board.generateMoves(from);
            for (int to : targets) {
                int score = 0;
                int targetPiece = board.boardArray[to];
                boolean isCapture = (targetPiece != 0) || (to == board.enPassantTarget);

                // 1. TT / PV Move Ordering (Highest priority)
                if (ttEntry != null && from == ttEntry.bestMoveFrom && to == ttEntry.bestMoveTo) {
                    score = 100000;
                }
                // 2. Capture ordering via MVV-LVA (Most Valuable Victim - Least Valuable Attacker)
                else if (isCapture) {
                    int victimVal = Math.abs(targetPiece) == 0 ? 100 : getPieceValue(targetPiece);
                    int attackerVal = getPieceValue(piece);
                    score = 90000 + (victimVal * 10) - attackerVal;
                }
                // 3. Killer Moves
                else if (from == killerMovesFrom[ply][0] && to == killerMovesTo[ply][0]) {
                    score = 80000;
                } else if (from == killerMovesFrom[ply][1] && to == killerMovesTo[ply][1]) {
                    score = 70000;
                }
                // 4. History Heuristics
                else {
                    score = Math.min(60000, historyHeuristic[from][to][side]);
                }

                sortedMoves.add(new Move(from, to, score, isCapture));
            }
        }

        // Sort fast using modern inline TimSort
        Collections.sort(sortedMoves, (a, b) -> Integer.compare(b.score, a.score));
        return sortedMoves;
    }

    private static int getPieceValue(int piece) {
        switch (Math.abs(piece)) {
            case 1: return 100;
            case 2: return 330;
            case 3: return 320;
            case 4: return 500;
            case 5: return 900;
            case 6: return 20000;
            default: return 0;
        }
    }

    private static boolean hasMajorPieces(Board board, boolean white) {
        for (int i = 0; i < 64; i++) {
            int p = board.boardArray[i];
            if (white && p > 1) return true; // Any non-pawn major piece
            if (!white && p < -1) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Bitwise Zobrist Hash Engine
    // -------------------------------------------------------------------------

    private static long computeZobristHash(Board board) {
        long hash = 0;
        for (int i = 0; i < 64; i++) {
            int p = board.boardArray[i];
            if (p != 0) {
                int pieceIdx = (p > 0) ? (p - 1) : (Math.abs(p) + 5);
                hash ^= zobristPieces[i][pieceIdx];
            }
        }
        if (board.whiteToMove) hash ^= zobristWhiteToMove;

        int castleRights = 0;
        if (board.whiteKingMoved) castleRights |= 1;
        if (board.whiteRookAMoved) castleRights |= 2;
        if (board.whiteRookHMoved) castleRights |= 4;
        if (board.blackKingMoved) castleRights |= 8;
        hash ^= zobristCastling[castleRights % 16];

        if (board.enPassantTarget != -1) {
            hash ^= zobristEnPassant[board.enPassantTarget];
        }
        return hash;
    }

    private static void storeTT(long hash, int depth, int score, byte flag, int from, int to) {
        int index = (int) (hash & (TT_SIZE - 1));
        TTEntry entry = transpositionTable[index];
        if (entry == null) {
            entry = new TTEntry();
            transpositionTable[index] = entry;
        }
        // Replacement strategy: Overwrite deep searches or match entries
        if (entry.zobristKey == 0 || entry.depth <= depth) {
            entry.zobristKey = hash;
            entry.depth = depth;
            entry.score = score;
            entry.flag = flag;
            entry.bestMoveFrom = from;
            entry.bestMoveTo = to;
        }
    }
}