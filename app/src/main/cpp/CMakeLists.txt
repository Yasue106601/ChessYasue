cmake_minimum_required(VERSION 3.22.1)
project("customchess")

set(FAIRY_SRC ${CMAKE_CURRENT_SOURCE_DIR}/../../../../fairy-stockfish/src)

# YASU: Copy NNUE into the native build directory first.
# This makes the embedded file available reliably in GitHub Actions.
set(NNUE_SOURCE_FILE
    ${FAIRY_SRC}/nn-3475407dc199.nnue
)

set(NNUE_BUILD_DIR
    ${CMAKE_CURRENT_BINARY_DIR}/nnue
)

set(NNUE_EMBEDDED_FILE
    ${NNUE_BUILD_DIR}/nn-3475407dc199.nnue
)

file(MAKE_DIRECTORY ${NNUE_BUILD_DIR})

configure_file(
    ${NNUE_SOURCE_FILE}
    ${NNUE_EMBEDDED_FILE}
    COPYONLY
)

add_library(
    customchess
    SHARED
    native-lib.cpp
    ${FAIRY_SRC}/custom_chess_api.cpp
    ${FAIRY_SRC}/benchmark.cpp
    ${FAIRY_SRC}/bitbase.cpp
    ${FAIRY_SRC}/bitboard.cpp
    ${FAIRY_SRC}/endgame.cpp
    ${FAIRY_SRC}/evaluate.cpp
    ${FAIRY_SRC}/material.cpp
    ${FAIRY_SRC}/misc.cpp
    ${FAIRY_SRC}/movegen.cpp
    ${FAIRY_SRC}/movepick.cpp
    ${FAIRY_SRC}/nnue/evaluate_nnue.cpp
    ${FAIRY_SRC}/nnue/features/half_ka_v2.cpp
    ${FAIRY_SRC}/nnue/features/half_ka_v2_variants.cpp
    ${FAIRY_SRC}/parser.cpp
    ${FAIRY_SRC}/partner.cpp
    ${FAIRY_SRC}/pawns.cpp
    ${FAIRY_SRC}/piece.cpp
    ${FAIRY_SRC}/position.cpp
    ${FAIRY_SRC}/psqt.cpp
    ${FAIRY_SRC}/search.cpp
    ${FAIRY_SRC}/syzygy/tbprobe.cpp
    ${FAIRY_SRC}/thread.cpp
    ${FAIRY_SRC}/timeman.cpp
    ${FAIRY_SRC}/tt.cpp
    ${FAIRY_SRC}/tune.cpp
    ${FAIRY_SRC}/uci.cpp
    ${FAIRY_SRC}/ucioption.cpp
    ${FAIRY_SRC}/variant.cpp
    ${FAIRY_SRC}/xboard.cpp
)

target_include_directories(
    customchess
    PRIVATE
    ${FAIRY_SRC}
)

target_compile_definitions(
    customchess
    PRIVATE
    NNUE_EMBED_PATH="${NNUE_EMBEDDED_FILE}"
)

target_compile_features(
    customchess
    PRIVATE
    cxx_std_17
)

target_compile_options(
    customchess
    PRIVATE
    -O3
    -ffast-math
)

find_library(log-lib log)

target_link_libraries(
    customchess
    ${log-lib}
)
