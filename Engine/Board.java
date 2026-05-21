package Engine;

import java.util.ArrayList;
import java.util.List;

public class Board {
    public int[] boardArray;
    public boolean whiteToMove;

    // Track whether kings or rooks have moved to determine castling rights
    public boolean whiteKingMoved;
    public boolean blackKingMoved;
    public boolean whiteRookAMoved;
    public boolean whiteRookHMoved;
    public boolean blackRookAMoved;
    public boolean blackRookHMoved;

    // Stores the index of the en passant target square (-1 if none available)
    public int enPassantTarget;

    // Stores the index of a pawn waiting to choose its promotion piece (-1 if none)
    public int pendingPromotionSquare = -1;

    public Board() {
        boardArray = new int[64];
        whiteToMove = true;
        enPassantTarget = -1;
        pendingPromotionSquare = -1;
        initializeBoard();
    }

    public void initializeBoard() {
        // Setup pieces: pawn=1, bishop=2, knight=3, rook=4, queen=5, king=6
        // Positive values belong to White, negative values belong to Black

        boardArray[0] = -4; boardArray[1] = -3; boardArray[2] = -2; boardArray[3] = -5;
        boardArray[4] = -6; boardArray[5] = -2; boardArray[6] = -3; boardArray[7] = -4;
        for (int i = 8;  i < 16; i++) boardArray[i] = -1;
        for (int i = 16; i < 48; i++) boardArray[i] =  0;
        for (int i = 48; i < 56; i++) boardArray[i] =  1;
        boardArray[56] = 4; boardArray[57] = 3; boardArray[58] = 2; boardArray[59] = 5;
        boardArray[60] = 6; boardArray[61] = 2; boardArray[62] = 3; boardArray[63] = 4;

        whiteKingMoved = false; blackKingMoved = false;
        whiteRookAMoved = false; whiteRookHMoved = false;
        blackRookAMoved = false; blackRookHMoved = false;
        enPassantTarget = -1;
        pendingPromotionSquare = -1;
    }

    // Check if a specific square can be attacked by the specified side
    public boolean isSquareAttacked(int square, boolean byWhite) {
        int sign = byWhite ? 1 : -1;

        int[][] rookDirs   = {{-1,0},{1,0},{0,-1},{0,1}};
        int[][] bishopDirs = {{-1,-1},{-1,1},{1,-1},{1,1}};

        // Scan in straight lines to find threatening rooks or queens
        for (int[] dir : rookDirs) {
            int r = square / 8, c = square % 8;
            while (true) {
                r += dir[0]; c += dir[1];
                if (r < 0 || r >= 8 || c < 0 || c >= 8) break;
                int p = boardArray[r * 8 + c];
                if (p == 0) continue;
                if (p == sign * 4 || p == sign * 5) return true;
                break;
            }
        }

        // Scan along diagonals to find threatening bishops or queens
        for (int[] dir : bishopDirs) {
            int r = square / 8, c = square % 8;
            while (true) {
                r += dir[0]; c += dir[1];
                if (r < 0 || r >= 8 || c < 0 || c >= 8) break;
                int p = boardArray[r * 8 + c];
                if (p == 0) continue;
                if (p == sign * 2 || p == sign * 5) return true;
                break;
            }
        }

        // Check for any enemy knights in range
        int row = square / 8, col = square % 8;
        int[][] knightOffsets = {{-2,-1},{-2,1},{2,-1},{2,1},{-1,-2},{-1,2},{1,-2},{1,2}};
        for (int[] off : knightOffsets) {
            int r = row + off[0], c = col + off[1];
            if (r >= 0 && r < 8 && c >= 0 && c < 8) {
                if (boardArray[r * 8 + c] == sign * 3) return true;
            }
        }

        // Check for enemy pawns capable of capturing diagonally
        int pawnDir = byWhite ? 1 : -1;
        int pawnRow = row + pawnDir;
        if (pawnRow >= 0 && pawnRow < 8) {
            if (col - 1 >= 0 && boardArray[pawnRow * 8 + (col - 1)] == sign * 1) return true;
            if (col + 1 <  8 && boardArray[pawnRow * 8 + (col + 1)] == sign * 1) return true;
        }

        // Check if the enemy king is sitting adjacent to this square
        int[][] kingDirs = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};
        for (int[] dir : kingDirs) {
            int r = row + dir[dir[0]], c = col + dir[1];
            if (r >= 0 && r < 8 && c >= 0 && c < 8) {
                if (boardArray[r * 8 + c] == sign * 6) return true;
            }
        }

        return false;
    }

    // Locate the king and see if it is currently in check
    public boolean isInCheck(boolean white) {
        int king = white ? 6 : -6;
        for (int i = 0; i < 64; i++) {
            if (boardArray[i] == king) {
                return isSquareAttacked(i, !white);
            }
        }
        return false;
    }

    // Generate every theoretical move on the board without checking if it leaves the king in danger
    public List<Integer> generateMoves() {
        List<Integer> moves = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            int piece = boardArray[i];
            if (piece == 0) continue;
            if ( whiteToMove && piece > 0) addPieceMoves(i, piece, moves);
            if (!whiteToMove && piece < 0) addPieceMoves(i, piece, moves);
        }
        return moves;
    }

    // Generate theoretical moves for just one single piece
    public List<Integer> generateMoves(int fromIndex) {
        List<Integer> moves = new ArrayList<>();
        int piece = boardArray[fromIndex];
        if (piece == 0) return moves;
        if ( whiteToMove && piece < 0) return moves;
        if (!whiteToMove && piece > 0) return moves;
        addPieceMoves(fromIndex, piece, moves);
        return moves;
    }

    // Collect all valid moves across the entire board for the active player
    public List<Integer> generateLegalMoves() {
        List<Integer> legal = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            int piece = boardArray[i];
            if (piece == 0) continue;
            if ( whiteToMove && piece > 0) legal.addAll(generateLegalMoves(i));
            if (!whiteToMove && piece < 0) legal.addAll(generateLegalMoves(i));
        }
        return legal;
    }

    // Test a piece's theoretical moves to make sure they do not expose the king to check
    public List<Integer> generateLegalMoves(int from) {
        List<Integer> pseudo = generateMoves(from);
        List<Integer> legal  = new ArrayList<>();
        boolean movingWhite  = boardArray[from] > 0;

        for (int to : pseudo) {
            MoveState state = makeMove(from, to);
            // Treat unpromoted pawns as queens for a split second to check if the move causes check
            if (pendingPromotionSquare != -1) {
                int sign = movingWhite ? 1 : -1;
                boardArray[pendingPromotionSquare] = sign * 5;
            }
            if (!isInCheck(movingWhite)) legal.add(to);
            undoMove(from, to, state);
        }
        return legal;
    }

    public boolean isCheckmate() {
        return isInCheck(whiteToMove) && generateLegalMoves().isEmpty();
    }

    public boolean isStalemate() {
        return !isInCheck(whiteToMove) && generateLegalMoves().isEmpty();
    }

    // Save state history data so moves can be reliably reversed
    public static class MoveState {
        int movedPiece, capturedPiece, enPassantTarget;
        boolean whiteToMove;
        boolean whiteKingMoved, blackKingMoved;
        boolean whiteRookAMoved, whiteRookHMoved;
        boolean blackRookAMoved, blackRookHMoved;
        boolean wasEnPassant, wasCastle;
        int rookFrom, rookTo;
        int epCapturedIndex;
        int pendingPromotionSquare;
    }

    public MoveState makeMove(int from, int to) {
        MoveState s = new MoveState();
        s.movedPiece              = boardArray[from];
        s.capturedPiece           = boardArray[to];
        s.enPassantTarget         = enPassantTarget;
        s.whiteToMove             = whiteToMove;
        s.whiteKingMoved          = whiteKingMoved;
        s.blackKingMoved          = blackKingMoved;
        s.whiteRookAMoved         = whiteRookAMoved;
        s.whiteRookHMoved         = whiteRookHMoved;
        s.blackRookAMoved         = blackRookAMoved;
        s.blackRookHMoved         = blackRookHMoved;
        s.wasEnPassant            = false;
        s.wasCastle               = false;
        s.pendingPromotionSquare  = pendingPromotionSquare;

        int piece    = boardArray[from];
        int absPiece = Math.abs(piece);

        // Process en passant capture rules
        if (absPiece == 1 && to == enPassantTarget) {
            s.wasEnPassant    = true;
            s.epCapturedIndex = to + (piece > 0 ? 8 : -8);
            s.capturedPiece   = boardArray[s.epCapturedIndex];
            boardArray[s.epCapturedIndex] = 0;
        }

        // Set en passant target tracking square if a pawn rushes forward two spaces
        enPassantTarget = -1;
        if (absPiece == 1 && Math.abs((to / 8) - (from / 8)) == 2) {
            enPassantTarget = (from + to) / 2;
        }

        // Process castling rules and automatically reposition the corresponding rook
        if (absPiece == 6 && Math.abs((to % 8) - (from % 8)) == 2) {
            s.wasCastle = true;
            if ((to % 8) == 6) {
                s.rookFrom = (piece > 0) ? 63 : 7;
                s.rookTo   = (piece > 0) ? 61 : 5;
            } else {
                s.rookFrom = (piece > 0) ? 56 : 0;
                s.rookTo   = (piece > 0) ? 59 : 3;
            }
            boardArray[s.rookTo]   = boardArray[s.rookFrom];
            boardArray[s.rookFrom] = 0;
        }

        // Complete the basic tile slide
        boardArray[to]   = piece;
        boardArray[from] = 0;

        // Flag pawns reaching the back rank for promotion picker without swapping turns yet
        pendingPromotionSquare = -1;
        if (absPiece == 1) {
            int row = to / 8;
            if ((row == 0 && piece > 0) || (row == 7 && piece < 0)) {
                pendingPromotionSquare = to;
                return s;
            }
        }

        // Terminate castling options if a critical piece shifts positions
        if (piece ==  6) whiteKingMoved = true;
        if (piece == -6) blackKingMoved = true;
        if (from == 56)  whiteRookAMoved = true;
        if (from == 63)  whiteRookHMoved = true;
        if (from ==  0)  blackRookAMoved = true;
        if (from ==  7)  blackRookHMoved = true;

        whiteToMove = !whiteToMove;
        return s;
    }

    // Replace the pawn with the piece selected by the user, then pass the turn
    public void applyPromotion(int absPieceChoice) {
        if (pendingPromotionSquare == -1) return;
        int sign = boardArray[pendingPromotionSquare] > 0 ? 1 : -1;
        boardArray[pendingPromotionSquare] = sign * absPieceChoice;
        pendingPromotionSquare = -1;

        whiteToMove = !whiteToMove;
    }

    // Step backward and reapply all previous variables from state history
    public void undoMove(int from, int to, MoveState s) {
        boardArray[from] = s.movedPiece;
        boardArray[to]   = s.wasEnPassant ? 0 : s.capturedPiece;

        if (s.wasEnPassant) {
            boardArray[s.epCapturedIndex] = s.capturedPiece;
        }

        if (s.wasCastle) {
            boardArray[s.rookFrom] = boardArray[s.rookTo];
            boardArray[s.rookTo]   = 0;
        }

        whiteToMove             = s.whiteToMove;
        whiteKingMoved          = s.whiteKingMoved;
        blackKingMoved          = s.blackKingMoved;
        whiteRookAMoved         = s.whiteRookAMoved;
        whiteRookHMoved         = s.whiteRookHMoved;
        blackRookAMoved         = s.blackRookAMoved;
        blackRookHMoved         = s.blackRookHMoved;
        enPassantTarget         = s.enPassantTarget;
        pendingPromotionSquare  = s.pendingPromotionSquare;
    }

    // Delegate generation rules to specific piece calculators
    private void addPieceMoves(int index, int piece, List<Integer> moves) {
        switch (Math.abs(piece)) {
            case 1: addPawnMoves(index, piece, moves);   break;
            case 2: addBishopMoves(index, piece, moves); break;
            case 3: addKnightMoves(index, piece, moves); break;
            case 4: addRookMoves(index, piece, moves);   break;
            case 5: addQueenMoves(index, piece, moves);  break;
            case 6: addKingMoves(index, piece, moves);   break;
        }
    }

    // Check if a tile is empty or occupied by an opponent's piece
    private boolean canMoveTo(int piece, int targetIndex) {
        int target = boardArray[targetIndex];
        if (target == 0) return true;
        return (piece > 0 && target < 0) || (piece < 0 && target > 0);
    }

    // Used for slider pieces like rooks, bishops, and queens to search along lines
    private void slidingMoves(int index, int piece, List<Integer> moves, int[][] dirs) {
        int row = index / 8, col = index % 8;
        for (int[] dir : dirs) {
            int r = row, c = col;
            while (true) {
                r += dir[0]; c += dir[1];
                if (r < 0 || r >= 8 || c < 0 || c >= 8) break;
                int target = r * 8 + c;
                if (canMoveTo(piece, target)) {
                    moves.add(target);
                    if (boardArray[target] != 0) break;
                } else break;
            }
        }
    }

    private void addPawnMoves(int index, int piece, List<Integer> moves) {
        int row = index / 8, col = index % 8;
        int dir      = (piece > 0) ? -1 : 1;
        int startRow = (piece > 0) ?  6 : 1;
        int nextRow  = row + dir;

        if (nextRow >= 0 && nextRow < 8) {
            int oneStep = nextRow * 8 + col;
            if (boardArray[oneStep] == 0) {
                moves.add(oneStep);
                if (row == startRow) {
                    int twoStep = (row + dir * 2) * 8 + col;
                    if (boardArray[twoStep] == 0) moves.add(twoStep);
                }
            }
            for (int dc : new int[]{-1, 1}) {
                int tc = col + dc;
                if (tc < 0 || tc >= 8) continue;
                int ti = nextRow * 8 + tc;
                int tp = boardArray[ti];
                boolean normal = tp != 0 && ((piece > 0 && tp < 0) || (piece < 0 && tp > 0));
                boolean ep     = ti == enPassantTarget;
                if (normal || ep) moves.add(ti);
            }
        }
    }

    private void addBishopMoves(int index, int piece, List<Integer> moves) {
        slidingMoves(index, piece, moves, new int[][]{{-1,-1},{-1,1},{1,-1},{1,1}});
    }

    private void addKnightMoves(int index, int piece, List<Integer> moves) {
        int row = index / 8, col = index % 8;
        for (int[] off : new int[][]{{-2,-1},{-2,1},{2,-1},{2,1},{-1,-2},{-1,2},{1,-2},{1,2}}) {
            int r = row + off[0], c = col + off[1];
            if (r >= 0 && r < 8 && c >= 0 && c < 8 && canMoveTo(piece, r * 8 + c))
                moves.add(r * 8 + c);
        }
    }

    private void addRookMoves(int index, int piece, List<Integer> moves) {
        slidingMoves(index, piece, moves, new int[][]{{-1,0},{1,0},{0,-1},{0,1}});
    }

    private void addQueenMoves(int index, int piece, List<Integer> moves) {
        slidingMoves(index, piece, moves,
                new int[][]{{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{-1,1},{1,-1},{1,1}});
    }

    private void addKingMoves(int index, int piece, List<Integer> moves) {
        int row = index / 8, col = index % 8;
        boolean isWhite = piece > 0;

        for (int[] dir : new int[][]{{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}}) {
            int r = row + dir[0], c = col + dir[1];
            if (r >= 0 && r < 8 && c >= 0 && c < 8 && canMoveTo(piece, r * 8 + c))
                moves.add(r * 8 + c);
        }

        boolean kingMoved = isWhite ? whiteKingMoved : blackKingMoved;
        if (kingMoved || isInCheck(isWhite)) return;

        // Check if the squares between the white king and white rooks are clear and safe
        if (isWhite) {
            if (!whiteRookHMoved
                    && boardArray[61] == 0 && boardArray[62] == 0
                    && !isSquareAttacked(61, false) && !isSquareAttacked(62, false)) {
                moves.add(62);
            }
            if (!whiteRookAMoved
                    && boardArray[57] == 0 && boardArray[58] == 0 && boardArray[59] == 0
                    && !isSquareAttacked(58, false) && !isSquareAttacked(59, false)) {
                moves.add(58);
            }
        // Check if the squares between the black king and black rooks are clear and safe
        } else {
            if (!blackRookHMoved
                    && boardArray[5] == 0 && boardArray[6] == 0
                    && !isSquareAttacked(5, true) && !isSquareAttacked(6, true)) {
                moves.add(6);
            }
            if (!blackRookAMoved
                    && boardArray[1] == 0 && boardArray[2] == 0 && boardArray[3] == 0
                    && !isSquareAttacked(2, true) && !isSquareAttacked(3, true)) {
                moves.add(2);
            }
        }
    }

    // Prints a text representation of the layout to the terminal console
    public void printBoard() {
        System.out.println("  a b c d e f g h");
        for (int row = 0; row < 8; row++) {
            System.out.print((8 - row) + " ");
            for (int col = 0; col < 8; col++) {
                int p = boardArray[row * 8 + col];
                String s;
                switch (Math.abs(p)) {
                    case 1: s="p"; break; case 2: s="b"; break; case 3: s="n"; break;
                    case 4: s="r"; break; case 5: s="q"; break; case 6: s="k"; break;
                    default: s="."; break;
                }
                System.out.print((p > 0 ? s.toUpperCase() : s) + " ");
            }
            System.out.println();
        }
        System.out.println(whiteToMove ? "White to move" : "Black to move");
    }

    // Duplicates the entire board profile so the engine can test combinations on a sandbox copy
    public Board cloneBoard() {
        Board clone = new Board();

        System.arraycopy(this.boardArray, 0, clone.boardArray, 0, 64);

        clone.whiteToMove = this.whiteToMove;

        clone.whiteKingMoved = this.whiteKingMoved;
        clone.blackKingMoved = this.blackKingMoved;
        clone.whiteRookAMoved = this.whiteRookAMoved;
        clone.whiteRookHMoved = this.whiteRookHMoved;
        clone.blackRookAMoved = this.blackRookAMoved;
        clone.blackRookHMoved = this.blackRookHMoved;

        clone.enPassantTarget = this.enPassantTarget;
        clone.pendingPromotionSquare = this.pendingPromotionSquare;

        return clone;
    }
}
