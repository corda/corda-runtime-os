package com.r3.corda.demo.interop.tokens.workflows

import com.r3.corda.demo.interop.tokens.states.TokenState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Instant


class QueryFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("QueryFlow.call() called")

        try {
            val unconsumedStates : List<TokenState> =
                ledgerService.findUnconsumedStatesByExactType(TokenState::class.java, 10, Instant.now())
                    .results.map { it.state.contractState }
            return jsonMarshallingService.format(unconsumedStates.map {
                TokenState(it.linearId.toString(), it.amount, it.issuer.toString(), it.owner.toString())  })

        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because: '${e.message}'")
            throw e
        }
    }
}

data class TokenState (
    val linearId: String,
    val amount: Int,
    val issuer: String,
    val owner: String,
)

