package net.corda.demo.connectfour

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.StartableByRPC
import net.corda.v5.application.flows.flowservices.FlowIdentity
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.application.services.json.JsonMarshallingService
import net.corda.v5.application.services.json.parseJson
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

@InitiatingFlow
@StartableByRPC
class StartConnectFourGameFlow(private val jsonArg: String) : Flow<String> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var flowIdentity: FlowIdentity

    @Suspendable
    override fun call(): String {
        log.info("Starting a game of connect4...")

        try {
            val startGame = jsonMarshallingService.parseJson<StartGameMessage>(jsonArg)

            val startingSlot = checkNotNull(startGame.startingSlotPlayed) { "No starting slot specified" }
            val player2 = checkNotNull(startGame.opponentX500Name) { "No opponent specified" }
            val player1 = flowIdentity.ourIdentity.name.toString()

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
            return jsonMarshallingService.formatJson(gameState)
        } catch (e: Exception) {
            log.error("Failed to start game for '$jsonArg' because '${e.message}'")
            throw e
        }
    }
}

