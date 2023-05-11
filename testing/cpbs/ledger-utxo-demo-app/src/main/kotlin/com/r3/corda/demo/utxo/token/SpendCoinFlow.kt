package com.r3.corda.demo.utxo.token

import java.math.BigDecimal
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import org.slf4j.LoggerFactory

@InitiatingFlow(protocol = "utxo-spend-coin-protocol")
class SpendCoinFlow : ClientStartableFlow {
    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var tokenSelection: TokenSelection

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Coin Spend...")
        try {
            val spendRequest = requestBody.getRequestBodyAs(jsonMarshallingService, SpendCoinMessage::class.java)
            val bankX500 = MemberX500Name.parse(spendRequest.issuerBankX500)
            val notary = notaryLookup.notaryServices.single()

            val selectionCriteria = TokenClaimCriteria(
                CoinState.tokenType,
                bankX500.toSecureHash(),
                notary.name,
                spendRequest.currency,
                BigDecimal(spendRequest.targetAmount)
            )

            val tokenClaim = tokenSelection.tryClaim(selectionCriteria)

            if (tokenClaim == null) {
                log.info("No tokens found for '${jsonMarshallingService.format(selectionCriteria)}'")
                return jsonMarshallingService.format(SpendCoinResponseMessage(listOf(), listOf(), listOf()))
            }

            log.info(
                "Found ${tokenClaim.claimedTokens.size} tokens found for '${
                    jsonMarshallingService.format(
                        selectionCriteria
                    )
                }'"
            )

            val spentCoins = tokenClaim.claimedTokens.take(spendRequest.maxCoinsToUse).map { it.stateRef }
            val coinsToRelease =
                tokenClaim.claimedTokens.drop(spendRequest.maxCoinsToUse).map { it.stateRef.toString() }

            val response = SpendCoinResponseMessage(
                foundCoins = tokenClaim.claimedTokens,
                spentCoins = spentCoins.map { it.toString() },
                releasedCoins = coinsToRelease
            )

            log.info("Releasing claim...")
            tokenClaim.useAndRelease(spentCoins)
            log.info("Claim released.")

            return jsonMarshallingService.format(response)
        } catch (e: Exception) {
            log.warn("Failed to process utxo flow for request body '$requestBody' because:'${e.message}'")
            throw e
        }
    }
}