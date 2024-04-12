package com.r3.corda.demo.utxo.token.selection

import com.r3.corda.demo.utxo.contract.TOKEN_AMOUNT
import com.r3.corda.demo.utxo.contract.TOKEN_ISSUER_HASH
import com.r3.corda.demo.utxo.contract.TOKEN_SYMBOL
import com.r3.corda.demo.utxo.contract.TOKEN_TYPE
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import org.slf4j.LoggerFactory

class TokenSelectionFlow3 : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var tokenSelection: TokenSelection

    @CordaInject
    lateinit var digestService: DigestService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup
    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting3 Token Selection Flow...")
        try {
            val queryCriteria = TokenClaimCriteria(
                TOKEN_TYPE,
                digestService.parseSecureHash(TOKEN_ISSUER_HASH),
                notaryLookup.notaryServices.single().name,
                TOKEN_SYMBOL,
                TOKEN_AMOUNT,
            )

            val claimResult1 = tokenSelection.tryClaim(queryCriteria)

            if (claimResult1 == null) {
                log.info("Token Selection result: 'None found' ")
                 return "FAIL"
            }

            // Now we just exit and let the postprocessing handler clean up for us
            // If we run this flow again we expect to get the same results as we never used
            // the claimed token and the flow completing should have freed the claim.
            return "SUCCESS"

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }
}