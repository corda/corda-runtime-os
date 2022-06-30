package net.cordapp.demo.connectfour

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

class StartConnectFourGameFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Starting a game of connect4...")

        try {
            val startGame = requestBody.getRequestBodyAs<StartGameMessage>(jsonMarshallingService)

            val startingSlot = checkNotNull(startGame.startingSlotPlayed) { "No starting slot specified" }
            val player2 = checkNotNull(startGame.opponentX500Name) { "No opponent specified" }
            val player1 = flowEngine.virtualNodeName.toString()

            val board = Array(7) { IntArray(6) { 0 } }
            board[startingSlot][0] = 1

            val gameState = GameStateMessage(
                gameStatus = GameStates.Playing,
                player1X500Name = player1,
                player2X500Name = player2,
                nextPlayersTurn = 2,
                boardState = board,
                lastMove = Move(player1, startingSlot)
            )
            log.info("Game Started for player 1 = '${player1}' player 2 ='${player2}'.")
            return jsonMarshallingService.format(gameState)
        } catch (e: Exception) {
            log.error("Failed to start game for '${requestBody.getRequestBody()}' because '${e.message}'")
            throw e
        }
    }
}

