package Engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The main AI brains of the chess engine. 
 * This class handles looking down the game tree, prioritizing the most promising moves,
 * caching past calculations, and making smart tactical cuts to find the best move efficiently.
 */
public class Search {

    // Unique flags for Transposition Table entries to categorize evaluation states
    private static final byte EXACT = 0;       // The score is exact and fully evaluated
    private static final byte LOWERBOUND = 1;  // Beta cutoff occurred: the true score is at least this high
    private static final byte UPPERBOUND = 2;  // Alpha fail-low occurred: the true score is at most this low

    private static final int INFINITY = 300000;
    private static final int MATE_VALUE = 290000;

    // A single cached slot in our Transposition Table (Hash Table)
    private static class TTEntry {
        long zobristKey;   // The unique 64-bit mathematical fingerprint of the board position
        int depth;         // How deep we searched from this position
        int score;         // The static evaluation or minimax score found
        byte flag;         // EXACT, LOWERBOUND, or UPPERBOUND boundary type
        int bestMoveFrom;  // Remember the best starting square from our previous search
        int bestMoveTo;    // Remember the best target square from our previous search
    }

    // 16MB Transposition Table size (approx. 500k positions, adjustable)
    private static final int TT_SIZE = 1 << 19;
    private static final TTEntry[] transpositionTable = new TTEntry[TT_SIZE];

    // Zobrist Hashing Matrices (used to assign unique bitwise numbers to any possible board layout)
    private static final long[][] zobristPieces = new long[64][13]; // 6 types * 2 colors + empty
    private static final long zobristWhiteToMove;
    private static final long[] zobristCastling = new long[16];
    private static final long[] zobristEnPassant = new long[64];

    // Killer & History Heuristics: Used to sort quiet (non-capture) moves.
    // Killer moves remember recent quiet moves that caused massive cutoffs at a specific depth layer.
    private static final int[][] killerMovesFrom = new int[100][2];
    private static final int[][] killerMovesTo = new int[100][2];
    // History table tracks which moves have historically performed well across the entire game tree.
    private static final int[][][] historyHeuristic = new int[64][64][2]; // [from][to][side]

    // Time Management controls
    private static long startTime;
    private static long allocatedTime;
    private static boolean stopSearch;

    static {
        // Initialize Zobrist Random Arrays statically once using a fixed seed for reproducible hashes
        Random r = new Random(42); 
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
     * Uses Iterative Deepening: searches 1 ply deep, then 2 plies, then 3 plies, etc.
     * This ensures we always have a high-quality fallback move if our time runs out mid-search.
     * 
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

        // Clear Killer heuristics before starting a fresh turn calculation
        for (int i = 0; i < 100; i++) {
            killerMovesFrom[i][0] = killerMovesFrom[i][1] = -1;
            killerMovesTo[i][0] = killerMovesTo[i][1] = -1;
        }

        // --- Iterative Deepening Framework ---
        for (int depth = 1; depth <= 64; depth++) {
            int alpha = -INFINITY;
            int beta = INFINITY;

            // Kick off the Negamax recursion for the current targeted ply depth
            int score = negamax(board, depth, 0, alpha, beta);

            // If time ran out during the middle of processing this depth layer, 
            // discard the incomplete data and stick to the last fully completed layer.
            if (stopSearch) {
                break; 
            }

            // Extract the Principal Variation (PV) / Best Move from our Transposition Table cache
            long currentHash = computeZobristHash(board);
            int index = (int) (currentHash & (TT_SIZE - 1));
            TTEntry entry = transpositionTable[index];

            if (entry != null && entry.zobristKey == currentHash) {
                bestMove[0] = entry.bestMoveFrom;
                bestMove[1] = entry.bestMoveTo;
                lastCompleteScore = score;
            }

            // Instant win/loss optimization: if we found a forced checkmate, stop wasting time searching deeper
            if (Math.abs(lastCompleteScore) > MATE_VALUE - 100) {
                break;
            }
        }

        // Safety check: If our best move is a pawn marching onto the end ranks, force a Queen promotion
        int absPiece = Math.abs(board.boardArray[bestMove[0]]);
        if (absPiece == 1 && (bestMove[1] / 8 == 0 || bestMove[1] / 8 == 7)) {
            bestMove[2] = 5; 
        }

        return bestMove;
    }

    // -------------------------------------------------------------------------
    // Core Negamax Loop
    // -------------------------------------------------------------------------

    private static int negamax(Board board, int depth, int ply, int alpha, int beta) {
        // Time Check: periodically poll the clock to see if we've exhausted our thinking allocation
        if ((ply & 15) == 0 && System.currentTimeMillis() - startTime >= allocatedTime) {
            stopSearch = true;
            return alpha;
        }

        // Fast-path draw detection
        if (Evaluate.isInsufficientMaterial(board) || board.isStalemate()) {
            return 0;
        }

        // --- Transposition Table Lookup ---
        // Check if we have seen this identical board setup before from an equal or deeper search.
        // If we have, we can return the cached score instantly and save millions of CPU cycles.
        long hash = computeZobristHash(board);
        int ttIndex = (int) (hash & (TT_SIZE - 1));
        TTEntry ttEntry = transpositionTable[ttIndex];

        if (ttEntry != null && ttEntry.zobristKey == hash && ttEntry.depth >= depth) {
            if (ttEntry.flag == EXACT) return ttEntry.score;
            if (ttEntry.flag == LOWERBOUND && ttEntry.score >= beta) return beta; // Beta cutoff fallback
            if (ttEntry.flag == UPPERBOUND && ttEntry.score <= alpha) return alpha; // Alpha fail-low fallback
        }

        // Base case: When we run out of depth, hand off to Quiescence Search to handle outstanding captures
        if (depth <= 0) {
            return quiescence(board, alpha, beta);
        }

        // Check Extension: If the current player is in check, grant them +1 bonus depth 
        // to prevent tactical blindspots right at the horizon threshold.
        boolean inCheck = board.isInCheck(board.whiteToMove);
        if (inCheck) depth++; 

        // --- Null Move Pruning (NMP) ---
        // If we skip our turn entirely ("null move") and our position is still so strong that 
        // the opponent can't drop us below beta, then we are overwhelmingly safe. We can pull off an early cutoff.
        if (!inCheck && depth >= 3 && hasMajorPieces(board, board.whiteToMove)) {
            board.whiteToMove = !board.whiteToMove; // Pass the turn to the opponent early
            int nmpScore = -negamax(board, depth - 1 - 2, ply + 1, -beta, -beta + 1);
            board.whiteToMove = !board.whiteToMove; // Reclaim turn control

            if (nmpScore >= beta) return beta;
        }

        // Sort moves to ensure we evaluate the highest quality candidates first (maximizes Alpha-Beta pruning)
        List<Move> moveList = getOrderedMoves(board, ply, ttEntry);
        if (moveList.isEmpty()) {
            return inCheck ? (-MATE_VALUE + ply) : 0; // Checkmate or Stalemate fallback handler
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
                board.applyPromotion(5); 
            }

            // Move Legality Filter: If this move exposed our own King to check, it's illegal. Undo it.
            if (board.isInCheck(!board.whiteToMove)) {
                board.undoMove(move.from, move.to, state);
                continue;
            }
            legalMovesCount++;

            int score;
            // --- Late Move Reductions (LMR) ---
            // If a move is sorted deep down the list (not a capture, not a check, etc.), it is statistically 
            // unlikely to be good. We search it at a reduced depth first to save time. 
            // If it surprises us and beats alpha, we perform a full-depth re-search to be absolutely sure.
            if (legalMovesCount > 4 && depth >= 3 && !move.isCapture && !inCheck) {
                score = -negamax(board, depth - 2, ply + 1, -alpha - 1, -alpha);
                if (score > alpha) { 
                    score = -negamax(board, depth - 1, ply + 1, -beta, -alpha); // Re-search
                }
            } else {
                // Regular Alpha-Beta Negamax search branch
                score = -negamax(board, depth - 1, ply + 1, -beta, -alpha);
            }

            board.undoMove(move.from, move.to, state);

            if (stopSearch) return alpha;

            // --- Beta Cutoff (Fail-High) ---
            // The opponent won't allow us to reach this branch anyway because we performed too well.
            // Cut the remaining branches early and record heuristics to prioritize this move in other states.
            if (score >= beta) {
                if (!move.isCapture) {
                    killerMovesFrom[ply][1] = killerMovesFrom[ply][0];
                    killerMovesTo[ply][1] = killerMovesTo[ply][0];
                    killerMovesFrom[ply][0] = move.from;
                    killerMovesTo[ply][0] = move.to;

                    int side = board.whiteToMove ? 0 : 1;
                    historyHeuristic[move.from][move.to][side] += depth * depth;
                }

                storeTT(hash, depth, beta, LOWERBOUND, move.from, move.to);
                return beta; 
            }

            // Alpha Update (We found a new principal best line for our current perspective)
            if (score > alpha) {
                alpha = score;
                bestMoveFrom = move.from;
                bestMoveTo = move.to;
            }
        }

        // Final double-check to confirm if any verified legal options actually occurred
        if (legalMovesCount == 0) {
            return inCheck ? (-MATE_VALUE + ply) : 0;
        }

        // Save our findings to the Transposition Table before leaving
        byte flag = (alpha <= origAlpha) ? UPPERBOUND : EXACT;
        storeTT(hash, depth, alpha, flag, bestMoveFrom, bestMoveTo);

        return alpha;
    }

    // -------------------------------------------------------------------------
    // Quiescence Search (Prevents the Horizon Effect)
    // -------------------------------------------------------------------------

    /**
     * Stabilizes tactical positions by continuing to search all outstanding captures 
     * even after regular depth limits hit zero. This prevents the "Horizon Effect", 
     * where an engine thinks it's winning because a catastrophic piece loss is hidden 
     * just beyond its max depth row.
     */
    private static int quiescence(Board board, int alpha, int beta) {
        if (System.currentTimeMillis() - startTime >= allocatedTime) {
            stopSearch = true;
            return alpha;
        }

        // Get a baseline static assessment score before trying any forced captures
        int standPat = Evaluate.evaluate(board);
        if (!board.whiteToMove) standPat = -standPat; 

        if (standPat >= beta) return beta;
        if (standPat > alpha) alpha = standPat;

        // Generate and filter strictly through capture options
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

    /**
     * Ranks and organizes move lists to maximize early pruning efficiency.
     * Evaluates candidates in this strict priority hierarchy:
     * 1. Hash/PV Move (The previously discovered best move)
     * 2. Captures via MVV-LVA (Most Valuable Victim - Least Valuable Attacker) -> e.g., Pawn taking Queen is rated highest
     * 3. Killer Moves (Quiet choices that recently worked well in sister branches)
     * 4. History Heuristics (Moves that historically score well over the full game tree)
     */
    private static List<Move> getOrderedMoves(Board board, int ply, TTEntry ttEntry) {
        List<Integer> pseudoMoves = board.generateMoves();
        List<Move> sortedMoves = new ArrayList<>(pseudoMoves.size() / 2);

        int side = board.whiteToMove ? 0 : 1;

        // Note: The loop below serves as an architectural shell for customized 
        // bit-packed move structures if you unpack multi-index lists natively.
        for (int i = 0; i < pseudoMoves.size(); i += 2) { }

        // Fallback robust collection mapping over the active board configurations
        for (int from = 0; from < 64; from++) {
            int piece = board.boardArray[from];
            if (piece == 0 || (board.whiteToMove && piece < 0) || (!board.whiteToMove && piece > 0)) continue;

            List<Integer> targets = board.generateMoves(from);
            for (int to : targets) {
                int score = 0;
                int targetPiece = board.boardArray[to];
                boolean isCapture = (targetPiece != 0) || (to == board.enPassantTarget);

                // Priority 1: Transposition Table match (PV Move)
                if (ttEntry != null && from == ttEntry.bestMoveFrom && to == ttEntry.bestMoveTo) {
                    score = 100000;
                }
                // Priority 2: MVV-LVA Capture ordering
                else if (isCapture) {
                    int victimVal = Math.abs(targetPiece) == 0 ? 100 : getPieceValue(targetPiece);
                    int attackerVal = getPieceValue(piece);
                    score = 90000 + (victimVal * 10) - attackerVal;
                }
                // Priority 3: Killer Moves
                else if (from == killerMovesFrom[ply][0] && to == killerMovesTo[ply][0]) {
                    score = 80000;
                } else if (from == killerMovesFrom[ply][1] && to == killerMovesTo[ply][1]) {
                    score = 70000;
                }
                // Priority 4: History Heuristics
                else {
                    score = Math.min(60000, historyHeuristic[from][to][side]);
                }

                sortedMoves.add(new Move(from, to, score, isCapture));
            }
        }

        // Fast inline sorting utilizing Java's TimSort framework to place highest scores first
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

    // Safety check used by Null Move Pruning to avoid skipping turns during endgame king-hunts
    private static boolean hasMajorPieces(Board board, boolean white) {
        for (int i = 0; i < 64; i++) {
            int p = board.boardArray[i];
            if (white && p > 1) return true; 
            if (!white && p < -1) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Bitwise Zobrist Hash Engine
    // -------------------------------------------------------------------------

    /**
     * Computes a highly uniform 64-bit fingerprint of the current board layout using XOR operators.
     * Incorporates piece locations, active turn state, and castling/en-passant validation tracking.
     */
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

    /**
     * Safely caches a freshly computed tree position inside our Transposition Table cache, 
     * using an replacement policy to overwrite shallower evaluations.
     */
    private static void storeTT(long hash, int depth, int score, byte flag, int from, int to) {
        int index = (int) (hash & (TT_SIZE - 1));
        TTEntry entry = transpositionTable[index];
        if (entry == null) {
            entry = new TTEntry();
            transpositionTable[index] = entry;
        }
        
        // Replacement strategy: Overwrite empty records or positions parsed from a shallower depth path
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
