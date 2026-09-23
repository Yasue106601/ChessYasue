#include "custom_chess_api.h"

#include <deque>
#include <sstream>
#include <stdexcept>

#include "bitboard.h"
#include "endgame.h"
#include "evaluate.h"
#include "movegen.h"
#include "nnue/network.h"
#include "psqt.h"
#include "search.h"
#include "tt.h"
#include "tune.h"
#include "uci.h"
#include "variant.h"

namespace Stockfish {
namespace CustomChess {

namespace {

constexpr const char* kStartFen =
    "rnbkqbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1";

constexpr const char* kVariantName = "customchess:chess";

constexpr const char* kVariantConfig = R"INI(
[customchess:chess]
startFen = rnbkqbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1
castling = false
doubleStep = false
enPassantRegion = -
mandatoryPawnPromotion = false
promotionPieceTypes = nbrq
nMoveRule = 0
nFoldRule = 0
delayedPawnPromotion = true
promotionLimit = q:1 r:2 b:2 n:2
)INI";

std::string color_string(Color c) {
    return c == WHITE ? "w" : "b";
}

}  // namespace

void Game::initialize_engine() {

    static bool initialized = false;

    if (initialized)
        return;

    pieceMap.init();
    variants.init();

    UCI::init(Options);
    Tune::init();

    Bitboards::init();
    Position::init();
    Bitbases::init();
    Endgames::init();

    PSQT::init(variants.find("chess")->second);

    // Load CustomChess directly from memory.
    std::stringstream ss(kVariantConfig);
    variants.parse_istream<false>(ss);

    Options["UCI_Variant"].set_combo(variants.get_keys());

    // Full-strength engine settings.
    Options["Threads"] = 1;
    Options["Hash"] = 128;
    Options["MultiPV"] = 1;
    Options["Skill Level"] = 20;
    Options["UCI_LimitStrength"] = false;
    Options["Use NNUE"] = true;

    Threads.set(1);
    Search::clear();
    Eval::NNUE::init();

    initialized = true;
}

Game::Game()
    : variant_(nullptr),
      states_(new std::deque<StateInfo>(1)) {

    initialize_engine();

    auto it = variants.find(kVariantName);

    if (it == variants.end())
        throw std::runtime_error("CustomChess variant was not loaded");

    variant_ = it->second;

    // Apply all variant-specific engine configuration.
    UCI::init_variant(variant_);

    if (!set_position(kStartFen))
        throw std::runtime_error("Failed to initialize CustomChess position");
}

Game::~Game() = default;

bool Game::set_position(const std::string& fen_string) {

    if (!variant_ || !Threads.main())
        return false;

    states_ = StateListPtr(new std::deque<StateInfo>(1));
    move_stack_.clear();

    pos_.set(
        variant_,
        fen_string,
        false,
        &states_->back(),
        Threads.main()
    );

    return true;
}

void Game::reset() {
    set_position(kStartFen);
}

bool Game::set_fen(const std::string& fen_string) {
    return set_position(fen_string);
}

std::string Game::fen() const {
    return pos_.fen();
}

std::string Game::side_to_move() const {
    return color_string(pos_.side_to_move());
}

std::string Game::board() const {

    std::string result;
    result.reserve(64);

    for (Rank r = RANK_1; r <= RANK_8; ++r)
    {
        for (File f = FILE_A; f <= FILE_H; ++f)
        {
            Square s = make_square(f, r);
            Piece pc = pos_.piece_on(s);

            if (pc == NO_PIECE)
                result += '.';
            else
                result += pos_.piece_to_char()[pc];
        }
    }

    return result;
}

std::string Game::piece_at(const std::string& square) const {

    if (square.size() != 2
        || square[0] < 'a' || square[0] > 'h'
        || square[1] < '1' || square[1] > '8')
        return "";

    const File file = File(square[0] - 'a');
    const Rank rank = Rank(square[1] - '1');
    const Square sq = make_square(file, rank);
    const Piece pc = pos_.piece_on(sq);

    if (pc == NO_PIECE)
        return ".";

    return std::string(1, pos_.piece_to_char()[pc]);
}

std::vector<std::string> Game::legal_moves() const {

    std::vector<std::string> result;

    for (const auto& move : MoveList<LEGAL>(pos_))
        result.push_back(UCI::move(pos_, move));

    return result;
}

std::vector<std::string> Game::legal_moves_from(const std::string& square) const {

    std::vector<std::string> result;

    if (square.size() != 2
        || square[0] < 'a' || square[0] > 'h'
        || square[1] < '1' || square[1] > '8')
        return result;

    for (const auto& move : MoveList<LEGAL>(pos_))
    {
        const std::string uci = UCI::move(pos_, move);

        if (uci.size() >= 2 && uci.compare(0, 2, square) == 0)
            result.push_back(uci);
    }

    return result;
}

std::vector<std::string> Game::promotion_moves() const {

    std::vector<std::string> result;

    for (const auto& move : MoveList<LEGAL>(pos_))
    {
        if (type_of(move) == PROMOTION
            && from_sq(move) == to_sq(move))
        {
            result.push_back(UCI::move(pos_, move));
        }
    }

    return result;
}

bool Game::can_promote() const {
    return !promotion_moves().empty();
}

std::string Game::best_move(int depth, int64_t nodes) {

    if (is_game_over())
        return "0000";

    Search::LimitsType limits;

    if (depth > 0)
        limits.depth = depth;

    if (nodes > 0)
        limits.nodes = nodes;

    if (depth <= 0 && nodes <= 0)
        limits.depth = 12;

    // Fairy-Stockfish takes ownership of the StateListPtr while searching.
    // Move the real game state into the search and restore it afterwards.
    StateListPtr searchStates = std::move(states_);

    Threads.start_thinking(
        pos_,
        searchStates,
        limits,
        false
    );

    Threads.wait_for_search_finished();

    // Restore ownership of the exact same state list.
    states_ = std::move(Threads.setupStates);

    Thread* bestThread = Threads.main();

    if (bestThread->rootMoves.empty())
        return "0000";

    Move best = bestThread->rootMoves[0].pv.empty()
              ? MOVE_NONE
              : bestThread->rootMoves[0].pv[0];

    if (best == MOVE_NONE)
        return "0000";

    return UCI::move(pos_, best);
}

bool Game::push(const std::string& uci) {

    Move selected = MOVE_NONE;

    for (const auto& move : MoveList<LEGAL>(pos_))
    {
        if (UCI::move(pos_, move) == uci)
        {
            selected = move;
            break;
        }
    }

    if (selected == MOVE_NONE)
        return false;

    states_->emplace_back();

    const bool givesCheck = pos_.gives_check(selected);

    pos_.do_move(selected, states_->back(), givesCheck);
    move_stack_.push_back(selected);

    return true;
}

bool Game::pop() {

    if (move_stack_.empty() || states_->size() <= 1)
        return false;

    pos_.undo_move(move_stack_.back());

    move_stack_.pop_back();
    states_->pop_back();

    return true;
}

std::size_t Game::move_count() const {
    return move_stack_.size();
}

bool Game::is_check() const {
    return bool(pos_.checkers());
}

bool Game::is_checkmate() const {

    if (!pos_.checkers())
        return false;

    return MoveList<LEGAL>(pos_).size() == 0;
}

bool Game::is_stalemate() const {

    if (pos_.checkers())
        return false;

    return MoveList<LEGAL>(pos_).size() == 0;
}

bool Game::is_insufficient_material() const {
    return Stockfish::has_insufficient_material(WHITE, pos_)
        && Stockfish::has_insufficient_material(BLACK, pos_);
}

bool Game::is_game_over() const {

    return is_checkmate()
        || is_stalemate()
        || is_insufficient_material();
}

std::string Game::result() const {

    if (is_checkmate())
        return pos_.side_to_move() == WHITE ? "0-1" : "1-0";

    if (is_stalemate() || is_insufficient_material())
        return "1/2-1/2";

    return "*";
}

}  // namespace CustomChess
}  // namespace Stockfish
