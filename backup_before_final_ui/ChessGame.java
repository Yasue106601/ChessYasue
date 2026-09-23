package com.customchess;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ChessGame {

    private long handle;

    public ChessGame() {
        handle = ChessNative.createGame();
        if (handle == 0) {
            throw new IllegalStateException("Failed to create chess game");
        }
    }

    public void close() {
        if (handle != 0) {
            ChessNative.destroyGame(handle);
            handle = 0;
        }
    }

    public void reset() {
        checkHandle();
        ChessNative.reset(handle);
    }

    public boolean setFen(String fen) {
        checkHandle();
        return ChessNative.setFen(handle, fen);
    }

    public String getFen() {
        checkHandle();
        return ChessNative.getFen(handle);
    }

    public String getBoard() {
        checkHandle();
        return ChessNative.getBoard(handle);
    }

    public String getPieceAt(String square) {
        checkHandle();
        return ChessNative.getPieceAt(handle, square);
    }

    public String getSideToMove() {
        checkHandle();
        return ChessNative.getSideToMove(handle);
    }

    public List<String> getLegalMoves() {
        checkHandle();
        return asList(ChessNative.getLegalMoves(handle));
    }

    public List<String> getLegalMovesFrom(String square) {
        checkHandle();
        return asList(ChessNative.getLegalMovesFrom(handle, square));
    }

    public List<String> getPromotionMoves() {
        checkHandle();
        return asList(ChessNative.getPromotionMoves(handle));
    }

    public boolean canPromote() {
        checkHandle();
        return ChessNative.canPromote(handle);
    }

    public boolean push(String uci) {
        checkHandle();
        return ChessNative.push(handle, uci);
    }

    public boolean pop() {
        checkHandle();
        return ChessNative.pop(handle);
    }

    public int getMoveCount() {
        checkHandle();
        return ChessNative.getMoveCount(handle);
    }

    public boolean isCheck() {
        checkHandle();
        return ChessNative.isCheck(handle);
    }

    public boolean isCheckmate() {
        checkHandle();
        return ChessNative.isCheckmate(handle);
    }

    public boolean isStalemate() {
        checkHandle();
        return ChessNative.isStalemate(handle);
    }

    public boolean isInsufficientMaterial() {
        checkHandle();
        return ChessNative.isInsufficientMaterial(handle);
    }

    public boolean isGameOver() {
        checkHandle();
        return ChessNative.isGameOver(handle);
    }

    public String getResult() {
        checkHandle();
        return ChessNative.getResult(handle);
    }

    public String getBestMove(int depth, long nodes) {
        checkHandle();
        return ChessNative.getBestMove(handle, depth, nodes);
    }

    private void checkHandle() {
        if (handle == 0) {
            throw new IllegalStateException("Chess game is closed");
        }
    }

    private static List<String> asList(String[] moves) {
        if (moves == null || moves.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.asList(moves);
    }
}
