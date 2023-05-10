package com.r3.corda.demo.utxo.token.selection

import java.math.BigDecimal
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "token-claim-query-flow-protocol")
class TokenClaimQueryFlow : ClientStartableFlow {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(TokenClaimQueryFlow::class.java)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var digestService: DigestService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var tokenSelection: TokenSelection

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val tokenClaimQueryMsg =
            requestBody.getRequestBodyAs(jsonMarshallingService, TokenClaimMsg::class.java)

        // Assume we are using a single notary
        val notary = notaryLookup.notaryServices.single()

        // Create our selection criteria to select a minimum of 100 GBP worth of coins
        val selectionCriteria = TokenClaimCriteria(
            tokenClaimQueryMsg.tokenType,
            digestService.hash(tokenClaimQueryMsg.issuerBankX500.toByteArray(), DigestAlgorithmName.SHA2_256),
            notary.name,
            tokenClaimQueryMsg.currency,
            tokenClaimQueryMsg.targetAmount
        )

        val tokenClaim = tokenSelection.tryClaim(selectionCriteria)

        return TokenClaimResponseMsg(tokenClaim != null).toJsonStr()
    }

    private fun TokenClaimResponseMsg.toJsonStr() =
        jsonMarshallingService.format(this)

    private class TokenClaimMsg(
        val tokenType: String,
        val issuerBankX500: String,
        val currency: String,
        val targetAmount: BigDecimal
    )

    private class TokenClaimResponseMsg(val tokenClaimed: Boolean)
}
