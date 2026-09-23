#pragma once

#include <deque>
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include "position.h"
#include "thread.h"

namespace Stockfish {
namespace CustomChess {

class Game {
public:
    Game();
    ~Game();

    Game(const Game&) = delete;
    Game& operator=(const Game&) = delete;

    void reset();
    bool set_fen(const std::string& fen);

    std::string fen() const;
    std::string side_to_move() const;
    std::string board() const;
    std::string piece_at(const std::string& square) const;

    std::vector<std::string> legal_moves() const;
    std::vector<std::string> legal_moves_from(const std::string& square) const;

    // Delayed promotion moves currently available.
    // Example: e8e8q, e8e8r, e8e8b, e8e8n
    std::vector<std::string> promotion_moves() const;
    bool can_promote() const;

    // Search the strongest move using the full Fairy-Stockfish search.
    std::string best_move(int depth = 12, int64_t nodes = 0);

    bool push(const std::string& uci);
    bool pop();

    std::size_t move_count() const;

    bool is_check() const;
    bool is_checkmate() const;
    bool is_stalemate() const;
    bool is_insufficient_material() const;
    bool is_game_over() const;

    std::string result() const;

private:
    const Variant* variant_;
    Position pos_;
    StateListPtr states_;
    std::vector<Move> move_stack_;

    static void initialize_engine();
    bool set_position(const std::string& fen);
};

}  // namespace CustomChess
}  // namespace Stockfish
