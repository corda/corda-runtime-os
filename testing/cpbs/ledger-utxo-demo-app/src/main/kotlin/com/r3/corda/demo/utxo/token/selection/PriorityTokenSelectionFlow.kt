package com.r3.corda.demo.utxo.token.selection

import com.r3.corda.demo.utxo.contract.TOKEN_AMOUNT
import com.r3.corda.demo.utxo.contract.TOKEN_ISSUER_HASH
import com.r3.corda.demo.utxo.contract.TOKEN_SYMBOL
import com.r3.corda.demo.utxo.contract.TOKEN_TYPE
import com.r3.corda.demo.utxo.contract.TestUtxoState
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.token.selection.Strategy
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("unused")
class PriorityTokenSelectionFlow : ClientStartableFlow {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var tokenSelection: TokenSelection

    @CordaInject
    lateinit var digestService: DigestService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("PriorityTokenSelectionFlow starting...")
        try {
            val queryCriteria = TokenClaimCriteria(
                TOKEN_TYPE,
                digestService.parseSecureHash(TOKEN_ISSUER_HASH),
                notaryLookup.notaryServices.single().name,
                TOKEN_SYMBOL,
                TOKEN_AMOUNT,
                Strategy.PRIORITY
            )

            val tokenClaim = requireNotNull(tokenSelection.tryClaim("claim1", queryCriteria))

            // Lookup the states that match the returned tokens
            val stateRefList = tokenClaim.claimedTokens.map { it.stateRef }
            val priorities = utxoLedgerService
                .resolve<TestUtxoState>(stateRefList)
                .map { it.state.contractState.priority }

            // Now we just exit and let the postprocessing handler clean up for us
            // If we run this flow again we expect to get the same results as we never used
            // the claimed token and the flow completing should have freed the claim.
            return jsonMarshallingService.format(priorities)

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}