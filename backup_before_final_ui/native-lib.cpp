#include <jni.h>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include "../../../../fairy-stockfish/src/custom_chess_api.h"

using Stockfish::CustomChess::Game;

namespace {

Game* get_game(jlong handle) {
    return reinterpret_cast<Game*>(static_cast<intptr_t>(handle));
}

jstring to_jstring(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

jobjectArray to_jstring_array(
    JNIEnv* env,
    const std::vector<std::string>& values) {

    jclass stringClass = env->FindClass("java/lang/String");
    if (!stringClass)
        return nullptr;

    jobjectArray result = env->NewObjectArray(
        static_cast<jsize>(values.size()),
        stringClass,
        nullptr
    );

    if (!result)
        return nullptr;

    for (jsize i = 0; i < static_cast<jsize>(values.size()); ++i) {
        jstring value = env->NewStringUTF(values[i].c_str());
        env->SetObjectArrayElement(result, i, value);
        env->DeleteLocalRef(value);
    }

    return result;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_customchess_ChessNative_createGame(
    JNIEnv* env,
    jclass) {

    try {
        return static_cast<jlong>(
            reinterpret_cast<intptr_t>(new Game())
        );
    } catch (const std::exception& e) {
        jclass exceptionClass = env->FindClass(
            "java/lang/RuntimeException"
        );
        if (exceptionClass)
            env->ThrowNew(exceptionClass, e.what());

        return 0;
    } catch (...) {
        jclass exceptionClass = env->FindClass(
            "java/lang/RuntimeException"
        );
        if (exceptionClass)
            env->ThrowNew(exceptionClass, "Failed to create chess game");

        return 0;
    }
}

JNIEXPORT void JNICALL
Java_com_customchess_ChessNative_destroyGame(
    JNIEnv*,
    jclass,
    jlong handle) {

    delete get_game(handle);
}

JNIEXPORT void JNICALL
Java_com_customchess_ChessNative_reset(
    JNIEnv*,
    jclass,
    jlong handle) {

    if (Game* game = get_game(handle))
        game->reset();
}

JNIEXPORT jboolean JNICALL
Java_com_customchess_ChessNative_setFen(
    JNIEnv* env,
    jclass,
    jlong handle,
    jstring fen) {

    Game* game = get_game(handle);

    if (!game || !fen)
        return JNI_FALSE;

    const char* chars = env->GetStringUTFChars(fen, nullptr);

    if (!chars)
        return JNI_FALSE;

    const bool result = game->set_fen(chars);

    env->ReleaseStringUTFChars(fen, chars);

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_customchess_ChessNative_getFen(
    JNIEnv* env,
    jclass,
    jlong handle) {

    Game* game = get_game(handle);

    return game
        ? to_jstring(env, game->fen())
        : env->NewStringUTF("");
}

JNIEXPORT jstring JNICALL
Java_com_customchess_ChessNative_getBoard(
    JNIEnv* env,
    jclass,
    jlong handle) {

    Game* game = get_game(handle);

    return game
        ? to_jstring(env, game->board())
        : env->NewStringUTF("");
}

JNIEXPORT jstring JNICALL
Java_com_customchess_ChessNative_getPieceAt(
    JNIEnv* env,
    jclass,
    jlong handle,
    jstring square) {

    Game* game = get_game(handle);

    if (!game || !square)
        return env->NewStringUTF("");

    const char* chars = env->GetStringUTFChars(square, nullptr);

    if (!chars)
        return env->NewStringUTF("");

    const std::string result = game->piece_at(chars);

    env->ReleaseStringUTFChars(square, chars);

    return to_jstring(env, result);
}

JNIEXPORT jstring JNICALL
Java_com_customchess_ChessNative_getSideToMove(
    JNIEnv* env,
    jclass,
    jlong handle) {

    Game* game = get_game(handle);

    return game
        ? to_jstring(env, game->side_to_move())
        : env->NewStringUTF("");
}

JNIEXPORT jobjectArray JNICALL
Java_com_customchess_ChessNative_getLegalMoves(
    JNIEnv* env,
    jclass,
    jlong handle) {

    Game* game = get_game(handle);

    if (!game)
        return nullptr;

    return to_jstring_array(env, game->legal_moves());
}

JNIEXPORT jobjectArray JNICALL
Java_com_customchess_ChessNative_getLegalMovesFrom(
    JNIEnv* env,
    jclass,
    jlong handle,
    jstring square) {

    Game* game = get_game(handle);

    if (!game || !square)
        return nullptr;

    const char* chars = env->GetStringUTFChars(square, nullptr);

    if (!chars)
        return nullptr;

    const std::vector<std::string> moves =
        game->legal_moves_from(chars);

    env->ReleaseStringUTFChars(square, chars);

    return to_jstring_array(env, moves);
}

JNIEXPORT jobjectArray JNICALL
Java_com_customchess_ChessNative_getPromotionMoves(
    JNIEnv* env,
    jclass,
    jlong handle) {

    Game* game = get_game(handle);

    if (!game)
        return nullptr;

    return to_jstring_array(env, game->promotion_moves());
}

JNIEXPORT jboolean JNICALL
Java_com_customchess_ChessNative_canPromote(
    JNIEnv*,
    jclass,
    jlong handle) {

    Game* game = get_game(handle);

    return game && game->can_promote()
        ? JNI_TRUE
        : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_customchess_ChessNative_push(
    JNIEnv* env,
    jclass,
    jlong handle,
    jstring uci) {

    Game* game = get_game(handle);

    if (!game || !uci)
        return JNI_FALSE;

    const char* chars = env->GetStringUTFChars(uci, nullptr);

    if (!chars)
        return JNI_FALSE;

    const bool result = game->push(chars);

    env->ReleaseStringUTFChars(uci, chars);

    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_customchess_ChessNative_pop(
    JNIEnv*,
    jclass,
    jlong handle) {

    Game* game = get_game(handle);

    return game && game->pop()
        ? JNI_TRUE
        : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_customchess_ChessNative_getMoveCount(
    JNIEnv*,
    jclass,
    jlong handle) {

    Game* game = get_game(handle);

    return game
        ? static_cast<jint>(game->move_count())
        : 0;
}

JNIEXPORT jboolean JNICALL
Java_com_customchess_ChessNative_isCheck(
    JNIEnv*,
    jclass,
    jlong handle) {

    Game* game = get_game(handle);

    return game && game->is_check()
        ? JNI_TRUE
        : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_customchess_ChessNative_isCheckmate(
    JNIEnv*,
    jclass,
    jlong handle) {

    Game* game = get_game(handle);

    return game && game->is_checkmate()
        ? JNI_TRUE
        : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_customchess_ChessNative_isStalemate(
    JNIEnv*,
    jclass,
    jlong handle) {

    Game* game = get_game(handle);

    return game && game->is_stalemate()
        ? JNI_TRUE
        : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_customchess_ChessNative_isInsufficientMaterial(
    JNIEnv*,
    jclass,
    jlong handle) {

    Game* game = get_game(handle);

    return game && game->is_insufficient_material()
        ? JNI_TRUE
        : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_customchess_ChessNative_isGameOver(
    JNIEnv*,
    jclass,
    jlong handle) {

    Game* game = get_game(handle);

    return game && game->is_game_over()
        ? JNI_TRUE
        : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_customchess_ChessNative_getResult(
    JNIEnv* env,
    jclass,
    jlong handle) {

    Game* game = get_game(handle);

    return game
        ? to_jstring(env, game->result())
        : env->NewStringUTF("*");
}

JNIEXPORT jstring JNICALL
Java_com_customchess_ChessNative_getBestMove(
    JNIEnv* env,
    jclass,
    jlong handle,
    jint depth,
    jlong nodes) {

    Game* game = get_game(handle);

    if (!game)
        return env->NewStringUTF("0000");

    try {
        return to_jstring(
            env,
            game->best_move(
                static_cast<int>(depth),
                static_cast<int64_t>(nodes)
            )
        );
    } catch (const std::exception& e) {
        jclass exceptionClass = env->FindClass(
            "java/lang/RuntimeException"
        );

        if (exceptionClass)
            env->ThrowNew(exceptionClass, e.what());

        return env->NewStringUTF("0000");
    } catch (...) {
        jclass exceptionClass = env->FindClass(
            "java/lang/RuntimeException"
        );

        if (exceptionClass)
            env->ThrowNew(
                exceptionClass,
                "Engine search failed"
            );

        return env->NewStringUTF("0000");
    }
}

}  // extern "C"
