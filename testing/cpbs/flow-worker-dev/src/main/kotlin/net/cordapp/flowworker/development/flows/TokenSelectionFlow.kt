package net.cordapp.flowworker.development.flows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.services.ClaimCriteria
import net.corda.v5.application.services.ClaimedTokens
import net.corda.v5.application.services.ClaimedTokensResultType
import net.corda.v5.application.services.TokenSelection
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.cordapp.flowworker.development.messages.TokenSelectionRequest
import net.cordapp.flowworker.development.messages.TokenSelectionResponse
import java.time.Duration

@Suppress("unused")
class TokenSelectionFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var tokenSelection: TokenSelection

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Starting Token Selection Flow...")
        try {
            val inputs = requestBody.getRequestBodyAs<TokenSelectionRequest>(jsonMarshallingService)

            val queryCriteria = getCriteriaFromRequest(inputs)

            log.info("Querying for tokens with: ${jsonMarshallingService.format(queryCriteria)}")
            val claimResult = tokenSelection.tryClaim(queryCriteria)
            log.info("Token Selection result: $ ${jsonMarshallingService.format(claimResult)}")

            val response = when (claimResult.resultType) {
                ClaimedTokensResultType.SUCCESS -> {
                    TokenSelectionResponse("SUCCESS", spendHalfTheClaimedTokens(claimResult))
                }
                ClaimedTokensResultType.AVAILABLE_CLAIMED -> {
                    TokenSelectionResponse("AVAILABLE_CLAIMED", listOf())
                }
                ClaimedTokensResultType.NONE_AVAILABLE -> {
                    TokenSelectionResponse("NONE_AVAILABLE", listOf())
                }
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
    private fun spendHalfTheClaimedTokens(claimResult: ClaimedTokens): List<Long> {
        val takeCount = (claimResult.tokens.size / 2) +1
        val tokensToSpend = claimResult.tokens.take(takeCount)

        log.info("Spending ${tokensToSpend.size} of ${claimResult.tokens.size} tokens claimed...")
        val spentTokenAmounts = tokensToSpend.map { it.amount }
        val spentTokenRefs = tokensToSpend.map { it.stateRef }

        // Spending takes some time let other flows execute while we spin and add some latency
        // to this test. This will also allow other flows to execute and claim before we release
        log.info("Sleep a while while spending tokens")
        flowEngine.sleep(Duration.ZERO)

        // release the tokens we have spent
        log.info("Releasing token claim...")
        claimResult.useAndRelease(spentTokenRefs)
        log.info("Token claim released.")
        return spentTokenAmounts
    }


    private fun getCriteriaFromRequest(inputRequest: TokenSelectionRequest): ClaimCriteria {
        if ((inputRequest.targetAmount ?: 0) <= 0L) {
            throw IllegalStateException("requested target amount must be > 0")
        }

        return ClaimCriteria(
            checkNotNull(inputRequest.tokenType) { "Token Type is required" },
            checkNotNull(inputRequest.issuerHash) { "Issuer Hash is required" },
            checkNotNull(inputRequest.notaryHash) { "Notary Hash is required" },
            checkNotNull(inputRequest.symbol) { "Symbolis required" },
            inputRequest.targetAmount!!
        ).apply {
            tagRegex = inputRequest.tagRegex
            ownerHash = inputRequest.ownerHash
        }
    }
}

