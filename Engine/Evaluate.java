package Engine;

public class Evaluate {

    // -------------------------------------------------------------------------
    // Material values (centipawns)
    // -------------------------------------------------------------------------
    public static final int PAWN_VALUE   =  100;
    public static final int BISHOP_VALUE =  335; // Slight adjustment for engine balance
    public static final int KNIGHT_VALUE =  325;
    public static final int ROOK_VALUE   =  500;
    public static final int QUEEN_VALUE  =  950; // Increased queen value for better endgames
    public static final int KING_VALUE   = 20000;

    private static final int[] PIECE_VALUES = { 0, PAWN_VALUE, BISHOP_VALUE,
            KNIGHT_VALUE, ROOK_VALUE, QUEEN_VALUE, KING_VALUE };

    // -------------------------------------------------------------------------
    // Game phase weights (used to compute a 0-256 phase score)
    // 256 = pure middlegame; 0 = pure endgame
    // -------------------------------------------------------------------------
    private static final int PHASE_PAWN   =  0;
    private static final int PHASE_BISHOP =  1;
    private static final int PHASE_KNIGHT =  1;
    private static final int PHASE_ROOK   =  2;
    private static final int PHASE_QUEEN  =  4;
    private static final int TOTAL_PHASE  = 16 * PHASE_PAWN
            + 4 * PHASE_BISHOP
            + 4 * PHASE_KNIGHT
            + 4 * PHASE_ROOK
            + 2 * PHASE_QUEEN;   // = 24

    // -------------------------------------------------------------------------
    // Piece-Square Tables (Explicitly defined from White's perspective)
    // index 0 = a1, index 63 = h8
    // -------------------------------------------------------------------------

    private static final int[] MG_PAWN_PST = {
            0,   0,   0,   0,   0,   0,   0,   0,
            5,  10,  10, -20, -20,  10,  10,   5,
            5,  -5, -10,   0,   0, -10,  -5,   5,
            0,   0,   0,  20,  20,   0,   0,   0,
            5,   5,  10,  25,  25,  10,   5,   5,
            10,  10,  20,  30,  30,  20,  10,  10,
            50,  50,  50,  50,  50,  50,  50,  50,
            0,   0,   0,   0,   0,   0,   0,   0
    };

    private static final int[] EG_PAWN_PST = {
            0,   0,   0,   0,   0,   0,   0,   0,
            10,  10,  10,  10,  10,  10,  10,  10,
            10,  10,  10,  10,  10,  10,  10,  10,
            20,  20,  20,  20,  20,  20,  20,  20,
            30,  30,  30,  30,  30,  30,  30,  30,
            50,  50,  50,  50,  50,  50,  50,  50,
            80,  80,  80,  80,  80,  80,  80,  80,
            0,   0,   0,   0,   0,   0,   0,   0
    };

    private static final int[] MG_KNIGHT_PST = {
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20,   0,   5,   5,   0, -20, -40,
            -30,   5,  10,  15,  15,  10,   5, -30,
            -30,   0,  15,  20,  20,  15,   0, -30,
            -30,   5,  15,  20,  20,  15,   5, -30,
            -30,   0,  10,  15,  15,  10,   0, -30,
            -40, -20,   0,   0,   0,   0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50
    };

    private static final int[] EG_KNIGHT_PST = {
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20,   0,   5,   5,   0, -20, -40,
            -30,   5,  10,  12,  12,  10,   5, -30,
            -30,   0,  12,  15,  15,  12,   0, -30,
            -30,   5,  12,  15,  15,  12,   5, -30,
            -30,   0,  10,  12,  12,  10,   0, -30,
            -40, -20,   0,   0,   0,   0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50
    };

    private static final int[] MG_BISHOP_PST = {
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10,   5,   0,   0,   0,   0,   5, -10,
            -10,  10,  10,  10,  10,  10,  10, -10,
            -10,   0,  10,  10,  10,  10,   0, -10,
            -10,   5,   5,  10,  10,   5,   5, -10,
            -10,   0,   5,  10,  10,   5,   0, -10,
            -10,   0,   0,   0,   0,   0,   0, -10,
            -20, -10, -10, -10, -10, -10, -10, -20
    };

    private static final int[] EG_BISHOP_PST = {
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10,   5,   5,   5,   5,   5,   5, -10,
            -10,   5,  10,  10,  10,  10,   5, -10,
            -10,   5,  10,  15,  15,  10,   5, -10,
            -10,   5,  10,  15,  15,  10,   5, -10,
            -10,   5,  10,  10,  10,  10,   5, -10,
            -10,   5,   5,   5,   5,   5,   5, -10,
            -20, -10, -10, -10, -10, -10, -10, -20
    };

    private static final int[] MG_ROOK_PST = {
            0,   0,   0,   5,   5,   0,   0,   0,
            -5,   0,   0,   0,   0,   0,   0,  -5,
            -5,   0,   0,   0,   0,   0,   0,  -5,
            -5,   0,   0,   0,   0,   0,   0,  -5,
            -5,   0,   0,   0,   0,   0,   0,  -5,
            -5,   0,   0,   0,   0,   0,   0,  -5,
            5,  10,  10,  10,  10,  10,  10,   5,
            0,   0,   0,   0,   0,   0,   0,   0
    };

    private static final int[] EG_ROOK_PST = {
            0,   0,   0,   0,   0,   0,   0,   0,
            0,   0,   0,   0,   0,   0,   0,   0,
            0,   0,   0,   0,   0,   0,   0,   0,
            0,   0,   0,   0,   0,   0,   0,   0,
            0,   0,   0,   0,   0,   0,   0,   0,
            5,   5,   5,   5,   5,   5,   5,   5,
            10,  10,  10,  10,  10,  10,  10,  10,
            10,  10,  10,  10,  10,  10,  10,  10
    };

    private static final int[] MG_QUEEN_PST = {
            -20, -10, -10,  -5,  -5, -10, -10, -20,
            -10,   0,   5,   0,   0,   0,   0, -10,
            -10,   5,   5,   5,   5,   5,   0, -10,
            -5,   0,   5,   5,   5,   5,   0,  -5,
            -5,   0,   5,   5,   5,   5,   0,  -5,
            -10,   0,   5,   5,   5,   5,   0, -10,
            -10,   0,   0,   0,   0,   0,   0, -10,
            -20, -10, -10,  -5,  -5, -10, -10, -20
    };

    private static final int[] EG_QUEEN_PST = {
            -20, -10, -10,  -5,  -5, -10, -10, -20,
            -10,   0,   5,   5,   5,   5,   0, -10,
            -10,   5,   5,   5,   5,   5,   5, -10,
            -5,   5,   5,   5,   5,   5,   5,  -5,
            -5,   5,   5,   5,   5,   5,   5,  -5,
            -10,   5,   5,   5,   5,   5,   5, -10,
            -10,   0,   5,   5,   5,   5,   0, -10,
            -20, -10, -10,  -5,  -5, -10, -10, -20
    };

    private static final int[] MG_KING_PST = {
            20,  30,  10,   0,   0,  10,  30,  20,
            20,  20,   0,   0,   0,   0,  20,  20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30
    };

    private static final int[] EG_KING_PST = {
            -50, -30, -30, -30, -30, -30, -30, -50,
            -30, -30,   0,   0,   0,   0, -30, -30,
            -30, -10,  20,  30,  30,  20, -10, -30,
            -30, -10,  30,  40,  40,  30, -10, -30,
            -30, -10,  30,  40,  40,  30, -10, -30,
            -30, -10,  20,  30,  30,  20, -10, -30,
            -30, -20, -10,   0,   0, -10, -20, -30,
            -50, -40, -30, -20, -20, -30, -40, -50
    };

    // -------------------------------------------------------------------------
    // Tuning Parameters (Centipawns)
    // -------------------------------------------------------------------------
    private static final int DOUBLED_PAWN_PENALTY   = -14;
    private static final int ISOLATED_PAWN_PENALTY  = -18;
    private static final int BACKWARD_PAWN_PENALTY  = -12;
    private static final int PASSED_PAWN_BONUS_MG   =  10;
    private static final int PASSED_PAWN_BONUS_EG   =  28;

    private static final int ROOK_OPEN_FILE_BONUS      = 28;
    private static final int ROOK_SEMI_OPEN_FILE_BONUS = 16;
    private static final int ROOK_ON_7TH_BONUS         = 36;

    private static final int BISHOP_PAIR_BONUS_MG      = 24;
    private static final int BISHOP_PAIR_BONUS_EG      = 38; // More valuable as pawns leave

    private static final int KNIGHT_OUTPOST_BONUS      = 22;

    private static final int MOBILITY_MG_WEIGHT        =  5;
    private static final int MOBILITY_EG_WEIGHT        =  7;

    private static final int KING_PAWN_SHIELD_BONUS    = 14;
    private static final int KING_OPEN_FILE_PENALTY    = -35;
    private static final int KING_SEMI_OPEN_PENALTY    = -18;

    private static final int TEMPO_BONUS               = 10;

    public static int evaluate(Board board) {
        if (isInsufficientMaterial(board)) return 0;

        int phase = computePhase(board);

        int mgScore = 0;
        int egScore = 0;

        int whitePawns   = 0, blackPawns   = 0;
        int whiteBishops = 0, blackBishops = 0;
        int whiteKingIdx = -1, blackKingIdx = -1;

        int whitePawnFiles = 0, blackPawnFiles = 0;

        for (int i = 0; i < 64; i++) {
            int piece = board.boardArray[i];
            if (piece == 0) continue;

            int abs = Math.abs(piece);
            int sign = piece > 0 ? 1 : -1;
            boolean isWhite = piece > 0;

            // Basic Material Accounting
            mgScore += sign * PIECE_VALUES[abs];
            egScore += sign * PIECE_VALUES[abs];

            // Map indexes smoothly
            int pIndex = isWhite ? i : mirrorIndex(i);

            switch (abs) {
                case 1: // Pawn
                    mgScore += sign * MG_PAWN_PST[pIndex];
                    egScore += sign * EG_PAWN_PST[pIndex];
                    if (isWhite) { whitePawns++;  whitePawnFiles |= (1 << (i % 8)); }
                    else         { blackPawns++;  blackPawnFiles |= (1 << (i % 8)); }
                    break;
                case 2: // Bishop
                    mgScore += sign * MG_BISHOP_PST[pIndex];
                    egScore += sign * EG_BISHOP_PST[pIndex];
                    if (isWhite) whiteBishops++; else blackBishops++;
                    break;
                case 3: // Knight
                    mgScore += sign * MG_KNIGHT_PST[pIndex];
                    egScore += sign * EG_KNIGHT_PST[pIndex];
                    // Outpost verification
                    if (isOutpost(board, i, isWhite)) {
                        mgScore += sign * KNIGHT_OUTPOST_BONUS;
                    }
                    break;
                case 4: // Rook
                    mgScore += sign * MG_ROOK_PST[pIndex];
                    egScore += sign * EG_ROOK_PST[pIndex];
                    break;
                case 5: // Queen
                    mgScore += sign * MG_QUEEN_PST[pIndex];
                    egScore += sign * EG_QUEEN_PST[pIndex];
                    break;
                case 6: // King
                    mgScore += sign * MG_KING_PST[pIndex];
                    egScore += sign * EG_KING_PST[pIndex];
                    if (isWhite) whiteKingIdx = i; else blackKingIdx = i;
                    break;
            }
        }

        // Complex Evaluation Layers
        int pawnStructureScore = evaluatePawnStructure(board, whitePawnFiles, blackPawnFiles, phase);
        mgScore += pawnStructureScore;
        egScore += pawnStructureScore;

        int mobilityDiff = evaluateMobility(board);
        mgScore += mobilityDiff * MOBILITY_MG_WEIGHT;
        egScore += mobilityDiff * MOBILITY_EG_WEIGHT;

        int rookScoring = evaluateRooks(board, whitePawnFiles, blackPawnFiles);
        mgScore += rookScoring;
        egScore += rookScoring;

        // Dynamic Scaling for Bishop Pairs
        if (whiteBishops >= 2) {
            mgScore += BISHOP_PAIR_BONUS_MG;
            egScore += BISHOP_PAIR_BONUS_EG;
        }
        if (blackBishops >= 2) {
            mgScore -= BISHOP_PAIR_BONUS_MG;
            egScore -= BISHOP_PAIR_BONUS_EG;
        }

        // Tapered King Safety
        if (whiteKingIdx != -1 && blackKingIdx != -1) {
            int whiteSafety = evaluateKingSafety(board, true, whiteKingIdx, whitePawnFiles, blackPawnFiles);
            int blackSafety = evaluateKingSafety(board, false, blackKingIdx, blackPawnFiles, whitePawnFiles);

            mgScore += (whiteSafety - blackSafety);
            // Safety factors degrade gracefully as pieces fall off the board
            egScore += ((whiteSafety - blackSafety) * phase) / 256;
        }

        if (board.whiteToMove) mgScore += TEMPO_BONUS;
        else                   mgScore -= TEMPO_BONUS;

        return taperedScore(mgScore, egScore, phase);
    }

    private static int computePhase(Board board) {
        int phase = 0;
        for (int i = 0; i < 64; i++) {
            int abs = Math.abs(board.boardArray[i]);
            switch (abs) {
                case 2: phase += PHASE_BISHOP; break;
                case 3: phase += PHASE_KNIGHT; break;
                case 4: phase += PHASE_ROOK;   break;
                case 5: phase += PHASE_QUEEN;  break;
            }
        }
        if (phase > TOTAL_PHASE) phase = TOTAL_PHASE;
        return (phase * 256) / TOTAL_PHASE;
    }

    private static int taperedScore(int mg, int eg, int phase) {
        return (mg * phase + eg * (256 - phase)) / 256;
    }

    private static int mirrorIndex(int idx) {
        int row = idx / 8;
        int col = idx % 8;
        return (7 - row) * 8 + col;
    }

    private static boolean isOutpost(Board board, int index, boolean isWhite) {
        int row = index / 8;
        int col = index % 8;

        // Outposts occur typically between ranks 4 and 6
        if (isWhite && (row < 2 || row > 4)) return false;
        if (!isWhite && (row < 3 || row > 5)) return false;

        // Verify if it can be attacked by an opposing pawn
        int enemyPawn = isWhite ? -1 : 1;
        int nextRow = isWhite ? row - 1 : row + 1; // Looking up/down vector files safely

        if (nextRow >= 0 && nextRow < 8) {
            if (col > 0 && board.boardArray[nextRow * 8 + (col - 1)] == enemyPawn) return false;
            if (col < 7 && board.boardArray[nextRow * 8 + (col + 1)] == enemyPawn) return false;
        }
        return true;
    }

    private static int evaluatePawnStructure(Board board, int whitePawnFiles, int blackPawnFiles, int phase) {
        int score = 0;
        int[] whitePawnsOnFile = new int[8];
        int[] blackPawnsOnFile = new int[8];
        int[] whiteMostAdvanced = new int[8];
        int[] blackMostAdvanced = new int[8];

        for (int f = 0; f < 8; f++) {
            whiteMostAdvanced[f] = 7;
            blackMostAdvanced[f] = 0;
        }

        for (int i = 0; i < 64; i++) {
            int piece = board.boardArray[i];
            if (Math.abs(piece) != 1) continue;
            int row = i / 8;
            int col = i % 8;
            if (piece > 0) {
                whitePawnsOnFile[col]++;
                if (row < whiteMostAdvanced[col]) whiteMostAdvanced[col] = row;
            } else {
                blackPawnsOnFile[col]++;
                if (row > blackMostAdvanced[col]) blackMostAdvanced[col] = row;
            }
        }

        for (int col = 0; col < 8; col++) {
            // White Structures
            if (whitePawnsOnFile[col] > 0) {
                if (whitePawnsOnFile[col] > 1) score += DOUBLED_PAWN_PENALTY * (whitePawnsOnFile[col] - 1);

                boolean leftOk  = col > 0 && whitePawnsOnFile[col - 1] > 0;
                boolean rightOk = col < 7 && whitePawnsOnFile[col + 1] > 0;
                if (!leftOk && !rightOk) score += ISOLATED_PAWN_PENALTY;

                int advRow = whiteMostAdvanced[col];
                boolean passed = true;
                for (int fc = Math.max(0, col - 1); fc <= Math.min(7, col + 1); fc++) {
                    if (blackPawnsOnFile[fc] > 0 && blackMostAdvanced[fc] >= advRow) {
                        passed = false;
                        break;
                    }
                }
                if (passed) {
                    int ranksAdvanced = 7 - advRow;
                    score += taperedScore(PASSED_PAWN_BONUS_MG * ranksAdvanced, PASSED_PAWN_BONUS_EG * ranksAdvanced, phase);
                }
            }

            // Black Structures
            if (blackPawnsOnFile[col] > 0) {
                if (blackPawnsOnFile[col] > 1) score -= DOUBLED_PAWN_PENALTY * (blackPawnsOnFile[col] - 1);

                boolean leftOk  = col > 0 && blackPawnsOnFile[col - 1] > 0;
                boolean rightOk = col < 7 && blackPawnsOnFile[col + 1] > 0;
                if (!leftOk && !rightOk) score -= ISOLATED_PAWN_PENALTY;

                int advRow = blackMostAdvanced[col];
                boolean passed = true;
                for (int fc = Math.max(0, col - 1); fc <= Math.min(7, col + 1); fc++) {
                    if (whitePawnsOnFile[fc] > 0 && whiteMostAdvanced[fc] <= advRow) {
                        passed = false;
                        break;
                    }
                }
                if (passed) {
                    int ranksAdvanced = advRow;
                    score -= taperedScore(PASSED_PAWN_BONUS_MG * ranksAdvanced, PASSED_PAWN_BONUS_EG * ranksAdvanced, phase);
                }
            }
        }
        return score;
    }

    private static int evaluateMobility(Board board) {
        int ownMoves = board.generateMoves().size();
        board.whiteToMove = !board.whiteToMove;
        int oppMoves = board.generateMoves().size();
        board.whiteToMove = !board.whiteToMove;

        int diff = ownMoves - oppMoves;
        return board.whiteToMove ? diff : -diff;
    }

    private static int evaluateRooks(Board board, int whitePawnFiles, int blackPawnFiles) {
        int score = 0;
        for (int i = 0; i < 64; i++) {
            int piece = board.boardArray[i];
            if (Math.abs(piece) != 4) continue;

            int col = i % 8;
            int row = i / 8;
            int sign = piece > 0 ? 1 : -1;
            boolean isWhite = piece > 0;

            int ownPawnFiles = isWhite ? whitePawnFiles : blackPawnFiles;
            int oppPawnFiles = isWhite ? blackPawnFiles : whitePawnFiles;
            int fileMask = 1 << col;

            if ((ownPawnFiles & fileMask) == 0) {
                if ((oppPawnFiles & fileMask) == 0) score += sign * ROOK_OPEN_FILE_BONUS;
                else                                score += sign * ROOK_SEMI_OPEN_FILE_BONUS;
            }

            int seventhRank = isWhite ? 1 : 6;
            if (row == seventhRank) score += sign * ROOK_ON_7TH_BONUS;
        }
        return score;
    }

    private static int evaluateKingSafety(Board board, boolean isWhite, int kingIdx, int ownPawnFiles, int oppPawnFiles) {
        int score = 0;
        int kingCol = kingIdx % 8;
        int kingRow = kingIdx / 8;

        // Dynamic pawn structural tracking shield checks relative to the current position
        int shieldRow = isWhite ? kingRow - 1 : kingRow + 1;
        if (shieldRow >= 0 && shieldRow < 8) {
            for (int dc = -1; dc <= 1; dc++) {
                int sc = kingCol + dc;
                if (sc < 0 || sc >= 8) continue;
                int pawn = board.boardArray[shieldRow * 8 + sc];
                if ((isWhite && pawn == 1) || (!isWhite && pawn == -1)) {
                    score += KING_PAWN_SHIELD_BONUS;
                }
            }
        }

        // Open Files adjacent tracking vectors
        for (int dc = -1; dc <= 1; dc++) {
            int fc = kingCol + dc;
            if (fc < 0 || fc >= 8) continue;
            int fileMask = 1 << fc;
            if ((ownPawnFiles & fileMask) == 0) {
                if ((oppPawnFiles & fileMask) == 0) score += KING_OPEN_FILE_PENALTY;
                else                                score += KING_SEMI_OPEN_PENALTY;
            }
        }
        return score;
    }

    public static boolean isInsufficientMaterial(Board board) {
        int whitePawns = 0, blackPawns = 0;
        int whiteRooks = 0, blackRooks = 0;
        int whiteQueens = 0, blackQueens = 0;
        int whiteBishops = 0, blackBishops = 0;
        int whiteKnights = 0, blackKnights = 0;

        for (int i = 0; i < 64; i++) {
            switch (board.boardArray[i]) {
                case  1: whitePawns++;   break;
                case -1: blackPawns++;   break;
                case  2: whiteBishops++; break;
                case -2: blackBishops++; break;
                case  3: whiteKnights++; break;
                case -3: blackKnights++; break;
                case  4: whiteRooks++;   break;
                case -4: blackRooks++;   break;
                case  5: whiteQueens++;  break;
                case -5: blackQueens++;  break;
            }
        }

        if (whitePawns > 0 || blackPawns > 0) return false;
        if (whiteRooks > 0 || blackRooks > 0) return false;
        if (whiteQueens > 0 || blackQueens > 0) return false;

        int whiteMinor = whiteBishops + whiteKnights;
        int blackMinor = blackBishops + blackKnights;

        if (whiteMinor == 0 && blackMinor == 0) return true;
        if (whiteMinor == 1 && blackMinor == 0) return true;
        if (whiteMinor == 0 && blackMinor == 1) return true;
        if (whiteKnights == 2 && whiteBishops == 0 && blackMinor == 0) return true;
        if (blackKnights == 2 && blackBishops == 0 && whiteMinor == 0) return true;

        return false;
    }
}