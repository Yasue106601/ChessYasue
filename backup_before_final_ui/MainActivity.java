package com.customchess;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private ChessGame game;
    private GridLayout board;
    private TextView status;

    private String selectedSquare;
    private final Set<String> selectedMoves = new HashSet<>();

    private static final String[] FILES =
            {"a", "b", "c", "d", "e", "f", "g", "h"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        game = new ChessGame();

        buildUi();
        refreshBoard();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(12, 12, 12, 12);

        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(18);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        root.addView(
                status,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        board = new GridLayout(this);
        board.setColumnCount(8);
        board.setRowCount(8);

        LinearLayout.LayoutParams boardParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                );

        boardParams.setMargins(0, 16, 0, 16);
        root.addView(board, boardParams);

        Button reset = new Button(this);
        reset.setText("Reset");

        reset.setOnClickListener(v -> {
            game.reset();
            clearSelection();
            refreshBoard();
        });

        root.addView(
                reset,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        setContentView(root);
    }

    private void refreshBoard() {
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

                char piece = boardString.charAt(boardIndex);

                TextView cell =
                        createCell(square, piece, row, col);

                board.addView(cell);
            }
        }

        updateStatus();
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

        cell.setTextSize(36);
        cell.setTypeface(
                Typeface.DEFAULT,
                Typeface.BOLD
        );

        boolean light =
                ((row + col) & 1) == 0;

        cell.setBackgroundColor(
                Color.parseColor(
                        light ? "#F0D9B5" : "#B58863"
                )
        );

        if (piece != '.') {
            cell.setText(
                    pieceToUnicode(piece)
            );
        } else {
            cell.setText("");
        }

        if (selectedSquare != null &&
                selectedSquare.equals(square)) {

            cell.setBackgroundColor(Color.YELLOW);

        } else if (selectedMoves.contains(square)) {

            cell.setBackgroundColor(Color.GREEN);
        }

        cell.setOnClickListener(
                v -> onSquareClicked(square)
        );

        return cell;
    }

    private void onSquareClicked(String square) {

        if (game.isGameOver()) {
            return;
        }

        /*
         * Delayed promotion:
         *
         * If the player taps a pawn that is already
         * on the last rank, show promotion choices.
         *
         * Promotion is optional. Cancelling the dialog
         * leaves the pawn unchanged.
         */
        String piece = game.getPieceAt(square);

        if (piece != null &&
                (piece.equals("P") || piece.equals("p")) &&
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
                game.getLegalMovesFrom(selectedSquare);

        String matchingMove = null;

        for (String move : legalMoves) {

            /*
             * Normal moves only.
             *
             * Delayed promotion moves are same-square
             * moves such as e8e8q and are handled by
             * tapping the pawn itself.
             */
            if (move.equals(baseMove)) {
                matchingMove = move;
                break;
            }
        }

        if (matchingMove != null) {

            if (game.push(matchingMove)) {
                clearSelection();
                refreshBoard();
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

        if (square == null || square.length() != 2) {
            return false;
        }

        char rank = square.charAt(1);

        return rank == '1' || rank == '8';
    }

    /*
     * Called later by the promotion UI.
     *
     * Promotion moves have the form:
     * e8e8q
     * e8e8r
     * e8e8b
     * e8e8n
     */
    private void showPromotionDialog(String square) {

        List<String> allPromotions =
                game.getPromotionMoves();

        if (allPromotions == null ||
                allPromotions.isEmpty()) {
            return;
        }

        /*
         * Keep only promotion moves belonging to
         * the pawn that was actually tapped.
         *
         * Example:
         * e8e8q
         * e8e8r
         * e8e8b
         * e8e8n
         */
        List<String> promotions =
                new java.util.ArrayList<>();

        for (String move : allPromotions) {

            if (move.length() >= 4 &&
                    move.substring(0, 2).equals(square) &&
                    move.substring(2, 4).equals(square)) {

                promotions.add(move);
            }
        }

        if (promotions.isEmpty()) {
            return;
        }

        String[] names =
                new String[promotions.size()];

        for (int i = 0; i < promotions.size(); i++) {

            String move = promotions.get(i);

            char p =
                    Character.toLowerCase(
                            move.charAt(move.length() - 1)
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
                                clearSelection();
                                refreshBoard();
                            }
                        }
                )
                /*
                 * Cancel is intentional:
                 * the pawn remains a pawn and can be
                 * promoted later.
                 */
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void updateStatus() {

        if (game.isCheckmate()) {

            status.setText("Checkmate");

        } else if (game.isStalemate()) {

            status.setText("Stalemate");

        } else if (game.isInsufficientMaterial()) {

            status.setText(
                    "Draw — Insufficient Material"
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

            status.setText(
                    "Turn: " +
                    game.getSideToMove()
            );
        }
    }

    private void clearSelection() {
        selectedSquare = null;
        selectedMoves.clear();
    }

    private String pieceToUnicode(char piece) {

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

        if (game != null) {
            game.close();
            game = null;
        }

        super.onDestroy();
    }
}
