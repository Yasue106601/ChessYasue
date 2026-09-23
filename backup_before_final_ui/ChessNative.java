package com.customchess;

public final class ChessNative {

    static {
        System.loadLibrary("customchess");
    }

    private ChessNative() {
    }

    // إنشاء/حذف لعبة
    public static native long createGame();
    public static native void destroyGame(long handle);

    // وضع البداية أو FEN
    public static native void reset(long handle);
    public static native boolean setFen(long handle, String fen);
    public static native String getFen(long handle);

    // معلومات الرقعة
    public static native String getBoard(long handle);
    public static native String getPieceAt(long handle, String square);
    public static native String getSideToMove(long handle);

    // الحركات القانونية
    public static native String[] getLegalMoves(long handle);
    public static native String[] getLegalMovesFrom(long handle, String square);

    // الترقية المؤجلة
    public static native String[] getPromotionMoves(long handle);
    public static native boolean canPromote(long handle);

    // تنفيذ وتراجع الحركات
    public static native boolean push(long handle, String uci);
    public static native boolean pop(long handle);
    public static native int getMoveCount(long handle);

    // حالة اللعبة
    public static native boolean isCheck(long handle);
    public static native boolean isCheckmate(long handle);
    public static native boolean isStalemate(long handle);
    public static native boolean isInsufficientMaterial(long handle);
    public static native boolean isGameOver(long handle);
    public static native String getResult(long handle);

    // أقوى نقلة من Fairy-Stockfish
    // depth <= 0 و nodes <= 0 = الإعداد الافتراضي للمحرك
    public static native String getBestMove(long handle, int depth, long nodes);
}
