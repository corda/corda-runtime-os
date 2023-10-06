package com.r3.corda.demo.utxo.token.selection

import com.r3.corda.demo.utxo.contract.TOKEN_AMOUNT
import com.r3.corda.demo.utxo.contract.TOKEN_ISSUER_HASH
import com.r3.corda.demo.utxo.contract.TOKEN_SYMBOL
import com.r3.corda.demo.utxo.contract.TOKEN_TYPE
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import org.slf4j.LoggerFactory

class TokenSelectionFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var tokenSelection: TokenSelection

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var digestService: DigestService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup
    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Token Selection Flow...")
        try {
            val queryCriteria = TokenClaimCriteria(
                TOKEN_TYPE,
                digestService.parseSecureHash(TOKEN_ISSUER_HASH),
                notaryLookup.notaryServices.single().name,
                TOKEN_SYMBOL,
                TOKEN_AMOUNT,
            )

            val claimResult = tokenSelection.tryClaim(queryCriteria)

            val response = if (claimResult == null) {
                log.info("Token Selection result: 'None found' ")
                "0"
            } else {
                log.info("Token Selection result: $ ${jsonMarshallingService.format(claimResult)}")
                claimResult.claimedTokens.size.toString()
            }

            log.info("Completing Token Selection Flow with: $response")
            return response

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}