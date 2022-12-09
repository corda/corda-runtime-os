package net.cordapp.testing.testflows.ledger

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import net.cordapp.testing.testflows.messages.CreateTokenRequest
import net.cordapp.testing.testflows.messages.CreateTokenResponse

/**
 * HACK: This class has been added for testing will be removed by CORE-5722 (ledger integration)
 */
class CreateTokenFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var tokenSelection: TokenSelection

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Starting token creation flow...")
        try {
            val input = requestBody.getRequestBodyAs<CreateTokenRequest>(jsonMarshallingService)
            val newToken = input.getClaimedToken()

            log.info("Pushing new token: ${jsonMarshallingService.format(newToken)}...")
            tokenSelection.pushTokenUpdates(listOf(newToken), listOf())
            log.info("New token created! ref=${newToken.stateRef}")

            return jsonMarshallingService.format(CreateTokenResponse(newToken.stateRef.toString()))

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}