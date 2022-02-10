package net.corda.demo.tictactoe

data class GameStateMessage(
    val gameStatus: GameStates,
    val player1X500Name: String,
    val player2X500Name: String,
    val nextPlayersTurn: Int,
    val boardState: Array<IntArray>,
    val lastMove: Move
)