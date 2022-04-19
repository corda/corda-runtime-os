package net.corda.demo.tictactoe

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.StartableByRPC
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.serialization.JsonMarshallingService
import net.corda.v5.application.serialization.parseJson
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

@InitiatingFlow
@StartableByRPC
class StartTicTacToeGameFlow(private val jsonArg: String) : Flow<String> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): String {
        log.info("Starting a game of Tic-tac-toe...")

        try {
            val startGame = jsonMarshallingService.parseJson<StartGameMessage>(jsonArg)

            val startingColumn = checkNotNull(startGame.startingColumnPlayed) { "No starting column specified" }
            val startingRow = checkNotNull(startGame.startingRowPlayed) { "No starting row specified" }
            val player2 = checkNotNull(startGame.opponentX500Name) { "No opponent specified" }
            val player1 = flowEngine.virtualNodeName.toString()

            val board = Array(3) { IntArray(3) { 0 } }
            board[startingColumn][startingRow] = 1

            val gameState = GameStateMessage(
                gameStatus = GameStates.Playing,
                player1X500Name = player1,
                player2X500Name = player2,
                nextPlayersTurn = 2,
                boardState = board,
                lastMove = Move(player1, startingColumn,startingRow)
            )
            log.info("Game Started for player 1 = '${player1}' player 2 ='${player2}'.")
            return jsonMarshallingService.formatJson(gameState)
        } catch (e: Throwable) {
            log.error("Failed to start game for '$jsonArg' because '${e.message}'")
            throw e
        }
    }
}

