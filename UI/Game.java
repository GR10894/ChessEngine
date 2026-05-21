package UI;

import Engine.Board;
import Engine.Search;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Game {
    // Game mode settings
    public static final int MODE_PLAY_AS_WHITE = 0;
    public static final int MODE_PLAY_AS_BLACK = 1;
    public static final int MODE_TWO_PLAYERS   = 2;

    public static int gameMode = MODE_PLAY_AS_WHITE;

    public static void chessWindow() {
        // Style the popup menu to match a dark theme
        UIManager.put("OptionPane.background", new Color(48, 46, 43));
        UIManager.put("Panel.background", new Color(48, 46, 43));
        UIManager.put("Button.background", new Color(115, 149, 82));
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("Button.font", new Font("SansSerif", Font.BOLD, 13));
        UIManager.put("Button.focus", new Color(0,0,0,0)); // hide the click border

        String[] options = {"Play as White", "Play as Black", "Local 2 Players"};

        JLabel messageLabel = new JLabel("Choose your Game Mode:");
        messageLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        messageLabel.setForeground(Color.WHITE);

        int choice = JOptionPane.showOptionDialog(
                null,
                messageLabel,
                "Chess Configuration",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
        );

        // Set the mode based on what the user clicked
        if (choice == 0) gameMode = MODE_PLAY_AS_WHITE;
        else if (choice == 1) gameMode = MODE_PLAY_AS_BLACK;
        else if (choice == 2) gameMode = MODE_TWO_PLAYERS;
        else gameMode = MODE_PLAY_AS_WHITE;

        // Set up the main window
        JFrame window = new JFrame();
        window.setTitle("Chess");
        window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        window.setResizable(false);
        window.setSize(700, 700);
        window.setLocationRelativeTo(null);

        ImageIcon icon = new ImageIcon("src/Resources/Logo/-6.png");
        window.setIconImage(icon.getImage());

        chessBoard chessBoard = new chessBoard();
        window.add(chessBoard);
        chessBoard.setBackground(new Color(48, 46, 43));

        window.setVisible(true);

        // If playing as black, the AI needs to make the opening move immediately
        if (gameMode == MODE_PLAY_AS_BLACK) {
            chessBoard.triggerAIMove();
        }
    }
}

class chessBoard extends JPanel {

    Board board = new Board();

    HashMap<Integer, Image> pieceImages = new HashMap<>();
    List<Integer> legalMoves = new ArrayList<>();

    int selectedRow = -1;
    int selectedCol = -1;
    boolean isPieceSelected = false;
    int fromIndex = -1;

    int lastMoveFromRow = -1;
    int lastMoveFromCol = -1;
    int lastMoveToRow   = -1;
    int lastMoveToCol   = -1;

    boolean gameOver = false;
    boolean aiThinking = false;
    String gameOverLine1 = "";
    String gameOverLine2 = "";

    Rectangle newGameBtnBounds = null;

    float glowPhase = 0f;
    Timer glowTimer;

    static final int[] PROMOTION_PIECES = { 5, 4, 2, 3 };
    Rectangle[] promotionBounds = new Rectangle[4];

    public chessBoard() {
        // Load images for all pieces (positive numbers for white, negative for black)
        pieceImages.put( 1, new ImageIcon("src/Resources/Images/1.png").getImage());
        pieceImages.put( 2, new ImageIcon("src/Resources/Images/2.png").getImage());
        pieceImages.put( 3, new ImageIcon("src/Resources/Images/3.png").getImage());
        pieceImages.put( 4, new ImageIcon("src/Resources/Images/4.png").getImage());
        pieceImages.put( 5, new ImageIcon("src/Resources/Images/5.png").getImage());
        pieceImages.put( 6, new ImageIcon("src/Resources/Images/6.png").getImage());
        pieceImages.put(-1, new ImageIcon("src/Resources/Images/-1.png").getImage());
        pieceImages.put(-2, new ImageIcon("src/Resources/Images/-2.png").getImage());
        pieceImages.put(-3, new ImageIcon("src/Resources/Images/-3.png").getImage());
        pieceImages.put(-4, new ImageIcon("src/Resources/Images/-4.png").getImage());
        pieceImages.put(-5, new ImageIcon("src/Resources/Images/-5.png").getImage());
        pieceImages.put(-6, new ImageIcon("src/Resources/Images/-6.png").getImage());

        // Timer to handle the glowing effect on the game over screen
        glowTimer = new Timer(30, e -> {
            glowPhase += 0.07f;
            if (glowPhase > (float)(2 * Math.PI)) glowPhase -= (float)(2 * Math.PI);
            repaint();
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {

                if (aiThinking) return;

                // Handle clicks on the pawn promotion menu
                if (board.pendingPromotionSquare != -1) {
                    for (int i = 0; i < 4; i++) {
                        if (promotionBounds[i] != null && promotionBounds[i].contains(e.getPoint())) {
                            board.applyPromotion(PROMOTION_PIECES[i]);
                            checkGameOver();

                            if (!gameOver && isAiTurn()) {
                                triggerAIMove();
                            }

                            repaint();
                            return;
                        }
                    }
                    return;
                }

                // Handle clicks on the new game button
                if (gameOver && newGameBtnBounds != null && newGameBtnBounds.contains(e.getPoint())) {
                    resetGame();
                    return;
                }

                if (gameOver) return;

                // Calculate which square on the board was clicked
                int Tile      = 64;
                int BoardSize = Tile * 8;
                int OffsetX   = (getWidth()  - BoardSize) / 2;
                int OffsetY   = (getHeight() - BoardSize) / 2;

                int row = (e.getY() - OffsetY) / Tile;
                int col = (e.getX() - OffsetX) / Tile;

                if (row < 0 || row >= 8 || col < 0 || col >= 8) return;

                int clickedIndex = row * 8 + col;
                int clickedPiece = board.boardArray[clickedIndex];

                // Handle selecting a piece or moving it
                if (!isPieceSelected) {
                    // Check if player clicked one of their own pieces on their turn
                    if (isHumanTurn() && ((board.whiteToMove && clickedPiece > 0) || (!board.whiteToMove && clickedPiece < 0))) {
                        isPieceSelected = true;
                        fromIndex   = clickedIndex;
                        selectedRow = row;
                        selectedCol = col;
                        legalMoves  = board.generateLegalMoves(fromIndex);
                    }
                } else {
                    boolean clickedOwn = (board.whiteToMove && clickedPiece > 0) || (!board.whiteToMove && clickedPiece < 0);

                    if (clickedOwn) {
                        // Switch selection to the newly clicked piece
                        fromIndex   = clickedIndex;
                        selectedRow = row;
                        selectedCol = col;
                        legalMoves  = board.generateLegalMoves(fromIndex);
                    } else if (legalMoves.contains(clickedIndex)) {
                        // Move the selected piece to the new square
                        lastMoveFromRow = selectedRow;
                        lastMoveFromCol = selectedCol;
                        lastMoveToRow   = row;
                        lastMoveToCol   = col;

                        board.makeMove(fromIndex, clickedIndex);

                        isPieceSelected = false;
                        fromIndex   = -1;
                        selectedRow = -1;
                        selectedCol = -1;
                        legalMoves.clear();

                        if (board.pendingPromotionSquare == -1) {
                            checkGameOver();

                            if (!gameOver && isAiTurn()) {
                                triggerAIMove();
                            }
                        }
                    } else {
                        // Reset if clicking an invalid square
                        isPieceSelected = false;
                        selectedRow = -1;
                        selectedCol = -1;
                        fromIndex   = -1;
                        legalMoves.clear();
                    }
                }

                repaint();
            }
        });
    }

    private boolean isHumanTurn() {
        if (Game.gameMode == Game.MODE_TWO_PLAYERS) return true;
        if (Game.gameMode == Game.MODE_PLAY_AS_WHITE) return board.whiteToMove;
        if (Game.gameMode == Game.MODE_PLAY_AS_BLACK) return !board.whiteToMove;
        return false;
    }

    private boolean isAiTurn() {
        if (Game.gameMode == Game.MODE_TWO_PLAYERS) return false;
        if (Game.gameMode == Game.MODE_PLAY_AS_WHITE) return !board.whiteToMove;
        if (Game.gameMode == Game.MODE_PLAY_AS_BLACK) return board.whiteToMove;
        return false;
    }

    public void triggerAIMove() {
        aiThinking = true;

        Board searchSandbox = this.board.cloneBoard();

        // Run AI search on a separate background thread so the UI doesn't freeze
        Thread aiThread = new Thread(() -> {
            try {
                int[] bestMove = Search.findBestMove(searchSandbox, 1500);

                int aiFrom  = bestMove[0];
                int aiTo    = bestMove[1];
                int aiPromo = bestMove[2];

                if (aiFrom != -1 && aiTo != -1) {
                    // Update UI components back on the main event thread
                    SwingUtilities.invokeLater(() -> {
                        lastMoveFromRow = aiFrom / 8;
                        lastMoveFromCol = aiFrom % 8;
                        lastMoveToRow   = aiTo / 8;
                        lastMoveToCol   = aiTo % 8;

                        board.makeMove(aiFrom, aiTo);

                        if (board.pendingPromotionSquare != -1) {
                            board.applyPromotion(aiPromo);
                        }

                        checkGameOver();
                        aiThinking = false;
                        repaint();
                    });
                } else {
                    aiThinking = false;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                aiThinking = false;
            }
        });

        aiThread.setPriority(Thread.MAX_PRIORITY);
        aiThread.start();
    }

    private void resetGame() {
        board = new Board();
        isPieceSelected = false;
        aiThinking = false;
        fromIndex = -1;
        selectedRow = -1;
        selectedCol = -1;
        legalMoves.clear();
        lastMoveFromRow = -1;
        lastMoveFromCol = -1;
        lastMoveToRow   = -1;
        lastMoveToCol   = -1;
        gameOver = false;
        gameOverLine1 = "";
        gameOverLine2 = "";
        newGameBtnBounds = null;
        glowTimer.stop();

        // Close the current window and pop open a brand new configuration screen
        setVisible(false);
        JFrame parentFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
        if (parentFrame != null) {
            parentFrame.dispose();
        }
        Game.chessWindow();
    }

    private void checkGameOver() {
        if (board.isCheckmate()) {
            gameOver = true;
            gameOverLine1 = board.whiteToMove ? "Black Wins!" : "White Wins!";
            gameOverLine2 = "by Checkmate";
            glowTimer.start();
        } else if (board.isStalemate()) {
            gameOver = true;
            gameOverLine1 = "Draw!";
            gameOverLine2 = "by Stalemate";
            glowTimer.start();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        int Tile      = 64;
        int BoardSize = Tile * 8;
        int OffsetX   = (getWidth()  - BoardSize) / 2;
        int OffsetY   = (getHeight() - BoardSize) / 2;

        super.paintComponent(g);

        boolean isCurrentSideInCheck = board.isInCheck(board.whiteToMove);
        int targetKingPiece = board.whiteToMove ? 6 : -6;

        // Draw the chessboard tiles and pieces
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int currentIndex = row * 8 + col;
                int piece = board.boardArray[currentIndex];

                // Render light and dark squares
                g.setColor((row + col) % 2 == 0
                        ? new Color(235, 236, 208)
                        : new Color(115, 149, 82));
                g.fillRect(OffsetX + col * Tile, OffsetY + row * Tile, Tile, Tile);

                // Highlight the king's square red if they are in check
                if (isCurrentSideInCheck && piece == targetKingPiece) {
                    g.setColor(new Color(236, 75, 75, 180));
                    g.fillRect(OffsetX + col * Tile, OffsetY + row * Tile, Tile, Tile);
                }

                // Highlight the squares from the last completed move
                if ((row == lastMoveFromRow && col == lastMoveFromCol)
                        || (row == lastMoveToRow && col == lastMoveToCol)) {
                    g.setColor(new Color(255, 255, 0, 120));
                    g.fillRect(OffsetX + col * Tile, OffsetY + row * Tile, Tile, Tile);
                }

                // Highlight currently selected piece's starting square
                if (row == selectedRow && col == selectedCol) {
                    g.setColor(new Color(255, 255, 0, 120));
                    g.fillRect(OffsetX + col * Tile, OffsetY + row * Tile, Tile, Tile);
                }

                // Draw dots/rings on squares the selected piece can safely move to
                if (legalMoves.contains(currentIndex)) {
                    int centerX = OffsetX + col * Tile + Tile / 2;
                    int centerY = OffsetY + row * Tile + Tile / 2;
                    g.setColor(new Color(0, 0, 0, 75));

                    if (board.boardArray[currentIndex] != 0) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        int thickness = Tile / 19;
                        g2.setStroke(new BasicStroke(thickness));
                        int pad = thickness;
                        g2.drawOval(OffsetX + col * Tile + pad, OffsetY + row * Tile + pad,
                                Tile - pad * 2, Tile - pad * 2);
                    } else {
                        int dot = Tile / 4;
                        g.fillOval(centerX - dot / 2, centerY - dot / 2, dot, dot);
                    }
                }

                // Draw the actual chess piece image
                if (piece != 0) {
                    g.drawImage(pieceImages.get(piece),
                            OffsetX + col * Tile, OffsetY + row * Tile, Tile, Tile, null);
                }
            }
        }

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw the text showing whose turn it is
        if (!gameOver && board.pendingPromotionSquare == -1) {
            String turnText;
            if (Game.gameMode == Game.MODE_TWO_PLAYERS) {
                turnText = board.whiteToMove ? "White's turn (Player 1)" : "Black's turn (Player 2)";
            } else {
                turnText = isHumanTurn() ? "Your turn" : "AI is thinking...";
            }

            g2d.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g2d.setColor(new Color(255, 255, 255, 160));
            FontMetrics fm = g2d.getFontMetrics();
            int textX = (getWidth() - fm.stringWidth(turnText)) / 2;
            int textY = OffsetY - 10;
            g2d.drawString(turnText, textX, textY);
        }

        // Draw the popup menu for selecting a promotion piece
        if (board.pendingPromotionSquare != -1) {
            boolean promotingWhite = board.boardArray[board.pendingPromotionSquare] > 0;

            int pickerW = Tile * 4 + 24;
            int pickerH = Tile + 24;
            int pickerX = OffsetX + (BoardSize - pickerW) / 2;
            int pickerY = OffsetY + (BoardSize - pickerH) / 2;

            g2d.setColor(new Color(0, 0, 0, 120));
            g2d.fillRect(OffsetX, OffsetY, BoardSize, BoardSize);

            g2d.setColor(new Color(30, 30, 30, 230));
            g2d.fillRoundRect(pickerX, pickerY, pickerW, pickerH, 14, 14);
            g2d.setColor(new Color(255, 255, 255, 35));
            g2d.setStroke(new BasicStroke(1.2f));
            g2d.drawRoundRect(pickerX, pickerY, pickerW, pickerH, 14, 14);

            int pad = 12;
            for (int i = 0; i < 4; i++) {
                int cellX = pickerX + pad + i * Tile;
                int cellY = pickerY + pad;

                promotionBounds[i] = new Rectangle(cellX, cellY, Tile, Tile);

                g2d.setColor(new Color(255, 255, 255, 18));
                g2d.fillRoundRect(cellX, cellY, Tile, Tile, 8, 8);

                int pieceVal = promotingWhite ? PROMOTION_PIECES[i] : -PROMOTION_PIECES[i];
                Image img = pieceImages.get(pieceVal);
                if (img != null) g2d.drawImage(img, cellX, cellY, Tile, Tile, null);
            }
        }

        // Draw the end-of-game card overlay
        if (gameOver) {
            g2d.setColor(new Color(0, 0, 0, 140));
            g2d.fillRect(OffsetX, OffsetY, BoardSize, BoardSize);

            int cardW = 300, cardH = 150;
            int cardX = OffsetX + (BoardSize - cardW) / 2;
            int cardY = OffsetY + (BoardSize - cardH) / 2;

            g2d.setColor(new Color(30, 30, 30, 220));
            g2d.fillRoundRect(cardX, cardY, cardW, cardH, 16, 16);
            g2d.setColor(new Color(255, 255, 255, 40));
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.drawRoundRect(cardX, cardY, cardW, cardH, 16, 16);

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 30));
            FontMetrics fm1 = g2d.getFontMetrics();
            g2d.drawString(gameOverLine1,
                    cardX + (cardW - fm1.stringWidth(gameOverLine1)) / 2,
                    cardY + 46);

            g2d.setFont(new Font("SansSerif", Font.PLAIN, 16));
            FontMetrics fm2 = g2d.getFontMetrics();
            g2d.setColor(new Color(200, 200, 200));
            g2d.drawString(gameOverLine2,
                    cardX + (cardW - fm2.stringWidth(gameOverLine2)) / 2,
                    cardY + 74);

            Color btnBase = new Color(115, 149, 82);

            int btnW = 160, btnH = 36;
            int btnX = cardX + (cardW - btnW) / 2;
            int btnY = cardY + cardH - btnH - 14;

            newGameBtnBounds = new Rectangle(btnX, btnY, btnW, btnH);

            int glowAlpha = (int)(40 + 30 * Math.sin(glowPhase));
            g2d.setColor(new Color(btnBase.getRed(), btnBase.getGreen(), btnBase.getBlue(), glowAlpha));
            g2d.fillRoundRect(btnX - 6, btnY - 6, btnW + 12, btnH + 12, 20, 20);

            g2d.setColor(btnBase);
            g2d.fillRoundRect(btnX, btnY, btnW, btnH, 12, 12);

            g2d.setColor(new Color(255, 255, 255, 40));
            g2d.fillRoundRect(btnX, btnY, btnW, btnH / 2, 12, 12);

            g2d.setStroke(new BasicStroke(1.2f));
            g2d.setColor(new Color(255, 255, 255, 80));
            g2d.drawRoundRect(btnX, btnY, btnW, btnH, 12, 12);

            g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
            FontMetrics fmBtn = g2d.getFontMetrics();
            String btnLabel = "New Game";
            g2d.setColor(Color.WHITE);
            g2d.drawString(btnLabel,
                    btnX + (btnW - fmBtn.stringWidth(btnLabel)) / 2,
                    btnY + (btnH + fmBtn.getAscent() - fmBtn.getDescent()) / 2);
        }
    }
}
