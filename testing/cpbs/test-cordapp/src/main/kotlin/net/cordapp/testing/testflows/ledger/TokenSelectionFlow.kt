package net.cordapp.testing.testflows.ledger

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.token.selection.TokenClaim
import net.corda.v5.ledger.utxo.token.selection.TokenClaimCriteria
import net.corda.v5.ledger.utxo.token.selection.TokenSelection
import net.cordapp.testing.testflows.messages.TokenSelectionRequest
import net.cordapp.testing.testflows.messages.TokenSelectionResponse
import org.slf4j.LoggerFactory
import java.math.BigDecimal

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

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Starting Token Selection Flow...")
        try {
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, TokenSelectionRequest::class.java)

            val queryCriteria = getCriteriaFromRequest(inputs)

            log.info("Querying for tokens with: ${jsonMarshallingService.format(queryCriteria)}")
            val claimResult = tokenSelection.tryClaim(queryCriteria)


            val response = if (claimResult == null) {
                log.info("Token Selection result: 'None found' ")
                TokenSelectionResponse("NONE_AVAILABLE", listOf())
            } else {
                log.info("Token Selection result: $ ${jsonMarshallingService.format(claimResult)}")
                TokenSelectionResponse("SUCCESS", spendHalfTheClaimedTokens(claimResult))
            }

            val responseMessage = jsonMarshallingService.format(response)
            log.info("Completing Token Selection Flow with: $responseMessage")
            return responseMessage

        } catch (e: Exception) {
            log.error("Unexpected error while processing the flow", e)
            throw e
        }
    }

    @Suspendable
    private fun spendHalfTheClaimedTokens(claimResult: TokenClaim): List<BigDecimal> {
        val takeCount = (claimResult.claimedTokens.size / 2) + 1
        val tokensToSpend = claimResult.claimedTokens.take(takeCount)

        log.info("Spending ${tokensToSpend.size} of ${claimResult.claimedTokens.size} tokens claimed...")
        val spentTokenAmounts = tokensToSpend.map { it.amount }
        val spentTokenRefs = tokensToSpend.map { it.stateRef }

        // release the tokens we have spent
        log.info("Releasing token claim...")
        claimResult.useAndRelease(spentTokenRefs)
        log.info("Token claim released.")
        return spentTokenAmounts
    }


    private fun getCriteriaFromRequest(inputRequest: TokenSelectionRequest): TokenClaimCriteria {
        check((inputRequest.targetAmount ?: 0) > 0L) {
            throw IllegalStateException("requested target amount must be > 0")
        }

        return TokenClaimCriteria(
            checkNotNull(inputRequest.tokenType) { "Token Type is required" },
            digestService.parseSecureHash(checkNotNull(inputRequest.issuerHash) { "Issuer Hash is required" }),
            MemberX500Name.parse(checkNotNull(inputRequest.notaryX500Name) { "Notary Hash is required" }),
            checkNotNull(inputRequest.symbol) { "Symbolis required" },
            BigDecimal(inputRequest.targetAmount!!)
        ).apply {
            tagRegex = inputRequest.tagRegex
            ownerHash = inputRequest.ownerHash?.let { digestService.parseSecureHash(it) }
        }
    }
}