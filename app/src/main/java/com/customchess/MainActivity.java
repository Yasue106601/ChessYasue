package com.customchess;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private ChessGame game;
    private GridLayout board;
    private TextView status;

    private Button undoButton;
    private Button resetButton;
    private Button modeButton;
    private Button resignButton;
    private Button drawButton;

    private String selectedSquare;
    private final Set<String> selectedMoves = new HashSet<>();
    private String lastMoveFrom;
    private String lastMoveTo;

    // Computer mode:
    // true  = computer opponent enabled
    // false = local two-player mode
    private boolean vsComputer = true;

    // Human color when playing against the computer.
    // true  = human White / computer Black
    // false = human Black / computer White
    private boolean humanIsWhite = true;

    // Engine search always runs away from the Android UI thread.
    private volatile boolean engineThinking = false;
    private volatile boolean destroyed = false;

    // App-level game endings: resignation / draw agreement.
    private boolean appGameOver = false;
    private String appResult = null;

    private final ExecutorService engineExecutor =
            Executors.newSingleThreadExecutor();

    // Initial practical engine strength.
    // We can tune this after the first real engine test.
    private static final int COMPUTER_DEPTH = 16;

    private static final int BOARD_LIGHT = Color.rgb(245, 245, 245);
    private static final int BOARD_DARK = Color.rgb(28, 28, 28);
    private static final int SELECTED_COLOR = Color.rgb(120, 120, 120);
    private static final int MOVE_COLOR = Color.rgb(190, 190, 190);
    private static final int LAST_MOVE_COLOR = Color.rgb(75, 75, 75);

    private static final String[] FILES =
            {"a", "b", "c", "d", "e", "f", "g", "h"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        game = new ChessGame();

        buildUi();
        refreshBoard();

        // If the human selected Black, the computer starts as White.
        maybeStartComputerTurn();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(8, 18, 8, 18);

        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(17);
        status.setTextColor(Color.WHITE);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setPadding(8, 8, 8, 14);

        root.addView(
                status,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        board = new SquareBoardLayout(this);
        board.setColumnCount(8);
        board.setRowCount(8);
        board.setBackgroundColor(Color.BLACK);

        LinearLayout.LayoutParams boardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        boardParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(board, boardParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, 14, 0, 0);

        undoButton = createButton("Undo");
        resetButton = createButton("Reset");
        modeButton = createButton("Computer");
        resignButton = createButton("Resign");
        drawButton = createButton("Draw");

        undoButton.setOnClickListener(v -> undoGame());
        resetButton.setOnClickListener(v -> resetGame());
        modeButton.setOnClickListener(v -> toggleMode());
        resignButton.setOnClickListener(v -> resignHuman());
        drawButton.setOnClickListener(v -> requestDrawAgreement());

        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(
                        115,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        buttonParams.setMargins(4, 0, 4, 0);

        controls.addView(undoButton, buttonParams);
        controls.addView(resetButton, buttonParams);
        controls.addView(modeButton, buttonParams);
        controls.addView(resignButton, buttonParams);
        controls.addView(drawButton, buttonParams);

        root.addView(
                controls,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        setContentView(root);
    }

    private Button createButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.rgb(45, 45, 45));
        return button;
    }

    private void refreshBoard() {
        if (destroyed || game == null) {
            return;
        }

        board.removeAllViews();

        String boardString = game.getBoard();

        /*
         * Native board order:
         * a1, b1, ... h1, a2, ... h8
         *
         * Android display order:
         * rank 8 at top, rank 1 at bottom.
         */
        for (int row = 0; row < 8; row++) {
            int rank = 8 - row;

            for (int col = 0; col < 8; col++) {
                String square = FILES[col] + rank;

                int boardIndex =
                        (rank - 1) * 8 + col;

                char piece =
                        boardString.charAt(boardIndex);

                TextView cell =
                        createCell(square, piece, row, col);

                board.addView(cell);
            }
        }

        updateStatus();
        updateControls();
    }

    private TextView createCell(
            String square,
            char piece,
            int row,
            int col
    ) {
        TextView cell = new TextView(this);

        GridLayout.LayoutParams params =
                new GridLayout.LayoutParams();

        params.width = 0;
        params.height = 0;

        params.columnSpec =
                GridLayout.spec(col, 1, 1f);

        params.rowSpec =
                GridLayout.spec(row, 1, 1f);

        cell.setLayoutParams(params);
        cell.setGravity(Gravity.CENTER);

        boolean light =
                ((row + col) & 1) == 0;

        int background =
                light ? BOARD_LIGHT : BOARD_DARK;

        if (selectedSquare != null &&
                selectedSquare.equals(square)) {

            background = SELECTED_COLOR;

        } else if (selectedMoves.contains(square)) {

            background = MOVE_COLOR;

        } else if (square.equals(lastMoveFrom) ||
                square.equals(lastMoveTo)) {

            background = LAST_MOVE_COLOR;
        }

        cell.setBackgroundColor(background);

        if (piece != '.') {

            cell.setText(pieceToUnicode(piece));
            cell.setTextSize(42);
            cell.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.NORMAL
            );

            if (Character.isUpperCase(piece)) {

                cell.setTextColor(Color.WHITE);
                cell.setShadowLayer(
                        3f,
                        1.5f,
                        1.5f,
                        Color.BLACK
                );

            } else {

                cell.setTextColor(Color.BLACK);
                cell.setShadowLayer(
                        3f,
                        1.5f,
                        1.5f,
                        Color.WHITE
                );
            }

        } else {

            cell.setText("");
        }

        cell.setOnClickListener(
                v -> onSquareClicked(square)
        );

        return cell;
    }

    private static class SquareBoardLayout
            extends GridLayout {

        public SquareBoardLayout(
                android.content.Context context
        ) {
            super(context);
        }

        @Override
        protected void onMeasure(
                int widthMeasureSpec,
                int heightMeasureSpec
        ) {
            int width =
                    MeasureSpec.getSize(
                            widthMeasureSpec
                    );

            int size =
                    Math.max(1, width);

            int exact =
                    MeasureSpec.makeMeasureSpec(
                            size,
                            MeasureSpec.EXACTLY
                    );

            super.onMeasure(exact, exact);
            setMeasuredDimension(size, size);
        }
    }

    private void onSquareClicked(String square) {

        if (engineThinking ||
                appGameOver ||
                game.isGameOver()) {

            return;
        }

        /*
         * Computer mode:
         * Human = White
         * Computer = Black
         */
        if (vsComputer &&
                !isHumanTurn()) {

            return;
        }

        /*
         * Delayed promotion:
         *
         * A pawn reaching the last rank remains
         * a pawn and may be promoted later.
         */
        String piece =
                game.getPieceAt(square);

        if (piece != null &&
                (piece.equals("P") ||
                 piece.equals("p")) &&
                isPromotionRank(square) &&
                game.canPromote()) {

            showPromotionDialog(square);
            return;
        }

        if (selectedSquare == null) {

            List<String> moves =
                    game.getLegalMovesFrom(square);

            if (moves.isEmpty()) {
                return;
            }

            selectedSquare = square;
            selectedMoves.clear();

            for (String move : moves) {

                if (move.length() >= 4 &&
                        !move.substring(0, 2)
                                .equals(move.substring(2, 4))) {

                    selectedMoves.add(
                            move.substring(2, 4)
                    );
                }
            }

            refreshBoard();
            return;
        }

        String baseMove =
                selectedSquare + square;

        List<String> legalMoves =
                game.getLegalMovesFrom(
                        selectedSquare
                );

        String matchingMove = null;

        for (String move : legalMoves) {

            if (move.equals(baseMove)) {
                matchingMove = move;
                break;
            }
        }

        if (matchingMove != null) {

            if (game.push(matchingMove)) {

                recordMove(matchingMove);
                clearSelection();
                refreshBoard();

                maybeStartComputerTurn();
                return;
            }
        }

        /*
         * Select another movable piece.
         */
        List<String> newMoves =
                game.getLegalMovesFrom(square);

        if (!newMoves.isEmpty()) {

            selectedSquare = square;
            selectedMoves.clear();

            for (String move : newMoves) {

                if (move.length() >= 4 &&
                        !move.substring(0, 2)
                                .equals(move.substring(2, 4))) {

                    selectedMoves.add(
                            move.substring(2, 4)
                    );
                }
            }

            refreshBoard();

        } else {

            clearSelection();
            refreshBoard();
        }
    }

    private boolean isPromotionRank(String square) {

        if (square == null ||
                square.length() != 2) {

            return false;
        }

        char rank =
                square.charAt(1);

        return rank == '1' ||
                rank == '8';
    }

    private void showPromotionDialog(
            String square
    ) {

        List<String> allPromotions =
                game.getPromotionMoves();

        if (allPromotions == null ||
                allPromotions.isEmpty()) {

            return;
        }

        List<String> promotions =
                new ArrayList<>();

        for (String move : allPromotions) {

            if (move.length() >= 4 &&
                    move.substring(0, 2)
                            .equals(square) &&
                    move.substring(2, 4)
                            .equals(square)) {

                promotions.add(move);
            }
        }

        if (promotions.isEmpty()) {
            return;
        }

        String[] names =
                new String[promotions.size()];

        for (int i = 0;
             i < promotions.size();
             i++) {

            char p =
                    Character.toLowerCase(
                            promotions.get(i)
                                    .charAt(
                                            promotions.get(i)
                                                    .length() - 1
                                    )
                    );

            switch (p) {

                case 'q':
                    names[i] = "Queen";
                    break;

                case 'r':
                    names[i] = "Rook";
                    break;

                case 'b':
                    names[i] = "Bishop";
                    break;

                case 'n':
                    names[i] = "Knight";
                    break;

                default:
                    names[i] = "Unknown";
                    break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Promote Pawn")
                .setItems(
                        names,
                        (dialog, which) -> {

                            String chosen =
                                    promotions.get(which);

                            if (game.push(chosen)) {

                                recordMove(chosen);
                                clearSelection();
                                refreshBoard();

                                maybeStartComputerTurn();
                            }
                        }
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void recordMove(String move) {

        if (move == null ||
                move.length() < 4) {

            lastMoveFrom = null;
            lastMoveTo = null;
            return;
        }

        lastMoveFrom =
                move.substring(0, 2);

        lastMoveTo =
                move.substring(2, 4);
    }

    private boolean isHumanTurn() {

        if (!vsComputer) {
            return true;
        }

        String side = game.getSideToMove();

        if (humanIsWhite) {
            return "white".equalsIgnoreCase(side);
        }

        return "black".equalsIgnoreCase(side);
    }

    /*
     * Starts Fairy-Stockfish on a background thread.
     *
     * IMPORTANT:
     * The Android UI thread never waits for the engine.
     */
    private void maybeStartComputerTurn() {

        if (destroyed ||
                !vsComputer ||
                engineThinking ||
                appGameOver ||
                game.isGameOver()) {

            return;
        }

        // Computer moves whenever it is not the human's turn.
        if (isHumanTurn()) {
            return;
        }

        engineThinking = true;

        clearSelection();
        updateControls();
        updateStatus();

        engineExecutor.execute(() -> {

            String bestMove = "0000";
            boolean pushed = false;

            try {

                if (!destroyed &&
                        !appGameOver &&
                        !game.isGameOver()) {

                    bestMove =
                            game.getBestMove(
                                    COMPUTER_DEPTH,
                                    0
                            );

                    if (bestMove != null &&
                            !"0000".equals(bestMove) &&
                            !destroyed &&
                            !appGameOver) {

                        pushed =
                                game.push(bestMove);
                    }
                }

            } catch (Throwable t) {

                final String message =
                        t.getClass().getSimpleName() +
                        (t.getMessage() == null
                                ? ""
                                : ": " + t.getMessage());

                if (!destroyed) {

                    runOnUiThread(() -> {

                        engineThinking = false;
                        updateControls();

                        status.setText(
                                "Engine error: " +
                                message
                        );
                    });
                }

                return;
            }

            final boolean movePlayed =
                    pushed;

            final String playedMove =
                    bestMove;

            if (destroyed) {

                engineThinking = false;

                if (game != null) {

                    game.close();
                    game = null;
                }

                engineExecutor.shutdown();
                return;
            }

            runOnUiThread(() -> {

                engineThinking = false;

                if (movePlayed) {
                    recordMove(playedMove);
                }

                clearSelection();
                refreshBoard();
            });
        });
    }

    private void undoGame() {

        if (engineThinking ||
                appGameOver ||
                game.isGameOver()) {

            return;
        }

        clearSelection();

        lastMoveFrom = null;
        lastMoveTo = null;

        if (vsComputer) {

            /*
             * Computer mode:
             * Undo the computer move and the human move.
             */
            game.pop();
            game.pop();

        } else {

            // Local two-player mode: undo one ply.
            game.pop();
        }

        refreshBoard();
    }

    private void resetGame() {

        if (engineThinking) {
            return;
        }

        game.reset();

        appGameOver = false;
        appResult = null;

        clearSelection();

        lastMoveFrom = null;
        lastMoveTo = null;

        refreshBoard();

        // If the human is Black, the computer starts again after Reset.
        maybeStartComputerTurn();
    }

    private void toggleMode() {

        if (engineThinking) {
            return;
        }

        /*
         * Cycle:
         *
         * 1. Computer — You: White
         * 2. Computer — You: Black
         * 3. 2 Players
         * 4. back to Computer — You: White
         */
        if (vsComputer && humanIsWhite) {

            // Human White -> Human Black
            humanIsWhite = false;

        } else if (vsComputer) {

            // Human Black -> Local 2 Players
            vsComputer = false;
            humanIsWhite = true;

        } else {

            // Local 2 Players -> Human White vs Computer
            vsComputer = true;
            humanIsWhite = true;
        }

        resetGame();
    }

    /*
     * Resignation belongs to the human player.
     * The computer never receives a Resign button.
     */
    private void resignHuman() {

        if (engineThinking ||
                appGameOver ||
                game.isGameOver()) {

            return;
        }

        String side =
                game.getSideToMove();

        String winner =
                "white".equalsIgnoreCase(side)
                        ? "Black"
                        : "White";

        new AlertDialog.Builder(this)
                .setTitle("Resign")
                .setMessage(
                        side +
                        " resigns. " +
                        winner +
                        " wins."
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Resign",
                        (dialog, which) -> {

                            appGameOver = true;

                            appResult =
                                    side +
                                    " resigned — " +
                                    winner +
                                    " wins";

                            clearSelection();
                            refreshBoard();
                        }
                )
                .show();
    }

    /*
     * Draw by agreement is an app-level result.
     *
     * In two-player mode both people share the device,
     * so the confirmation represents their agreement.
     *
     * In Computer mode the human can end the game
     * by agreement through the confirmation dialog.
     */
    private void requestDrawAgreement() {

        if (engineThinking ||
                appGameOver ||
                game.isGameOver()) {

            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Draw by Agreement")
                .setMessage(
                        "End the game as a draw by agreement?"
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Agree",
                        (dialog, which) -> {

                            appGameOver = true;

                            appResult =
                                    "Draw by agreement";

                            clearSelection();
                            refreshBoard();
                        }
                )
                .show();
    }

    private void updateControls() {

        if (undoButton == null) {
            return;
        }

        boolean nativeOver =
                game != null &&
                game.isGameOver();

        boolean active =
                !engineThinking &&
                !appGameOver &&
                !nativeOver;

        boolean hasMoves =
                game != null &&
                game.getMoveCount() > 0;

        undoButton.setEnabled(
                active && hasMoves
        );

        resetButton.setEnabled(
                !engineThinking
        );

        modeButton.setEnabled(
                !engineThinking
        );

        /*
         * Resign and Draw are human-player actions.
         * They are not available while the computer thinks.
         */
        resignButton.setEnabled(
                active &&
                isHumanTurn()
        );

        drawButton.setEnabled(
                active &&
                isHumanTurn()
        );

        if (!vsComputer) {

            modeButton.setText("2 Players");

        } else if (humanIsWhite) {

            modeButton.setText("You: White");

        } else {

            modeButton.setText("You: Black");
        }
    }

    private void updateStatus() {

        if (appGameOver) {

            status.setText(appResult);
            return;
        }

        if (game.isCheckmate()) {

            status.setText("Checkmate");

        } else if (game.isStalemate()) {

            status.setText("Stalemate");

        } else if (game.isInsufficientMaterial()) {

            status.setText(
                    "Draw — Insufficient Material"
            );

        } else if (engineThinking) {

            status.setText(
                    "Black — Computer thinking..."
            );

        } else if (game.isCheck()) {

            status.setText(
                    game.getSideToMove() +
                    " — Check"
            );

        } else if (game.canPromote()) {

            status.setText(
                    game.getSideToMove() +
                    " — Promotion Available"
            );

        } else {

            String modeText;

            if (!vsComputer) {

                modeText = "2 Players  |  ";

            } else if (humanIsWhite) {

                modeText = "You: White  |  Computer: Black  |  ";

            } else {

                modeText = "You: Black  |  Computer: White  |  ";
            }

            status.setText(
                    modeText +
                    "Turn: " +
                    game.getSideToMove()
            );
        }
    }

    private void clearSelection() {

        selectedSquare = null;
        selectedMoves.clear();
    }

    private String pieceToUnicode(
            char piece
    ) {

        switch (piece) {

            case 'K': return "♔";
            case 'Q': return "♕";
            case 'R': return "♖";
            case 'B': return "♗";
            case 'N': return "♘";
            case 'P': return "♙";

            case 'k': return "♚";
            case 'q': return "♛";
            case 'r': return "♜";
            case 'b': return "♝";
            case 'n': return "♞";
            case 'p': return "♟";

            default:
                return "";
        }
    }

    @Override
    protected void onDestroy() {

        destroyed = true;

        engineExecutor.shutdownNow();

        /*
         * If the engine is currently searching,
         * the background task will close the native
         * game after the search finishes.
         */
        if (!engineThinking &&
                game != null) {

            game.close();
            game = null;
        }

        super.onDestroy();
    }
}
