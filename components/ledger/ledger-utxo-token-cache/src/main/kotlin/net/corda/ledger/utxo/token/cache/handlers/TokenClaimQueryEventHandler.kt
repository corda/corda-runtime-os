package net.corda.ledger.utxo.token.cache.handlers

import net.corda.data.flow.event.FlowEvent
import net.corda.messaging.api.records.Record
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.ClaimQuery
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.ledger.utxo.token.cache.services.TokenFilterStrategy
import java.math.BigDecimal
import net.corda.ledger.utxo.token.cache.Helper.toDto
import net.corda.ledger.utxo.token.cache.services.AvailableTokenService

class TokenClaimQueryEventHandler(
    private val filterStrategy: TokenFilterStrategy,
    private val recordFactory: RecordFactory,
    private val availableTokenService: AvailableTokenService
) : TokenEventHandler<ClaimQuery> {

    override fun handle(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: ClaimQuery
    ): Record<String, FlowEvent> {

        // Fetch the tokens directly from the database
        val availableTokens = availableTokenService.findAvailTokens(
            event.poolKey.toDto(),
            event.ownerHash,
            event.tagRegex
        )

        val selectedTokens = mutableListOf<CachedToken>()
        var selectedAmount = BigDecimal.ZERO

        for (token in filterStrategy.filterTokens(availableTokens.token, event)) {
            if (selectedAmount >= event.targetAmount) {
                break
            }

            if (state.isTokenClaimed(token.stateRef)) {
                continue
            }

            selectedAmount += token.amount
            selectedTokens += token
        }

        return if (selectedAmount >= event.targetAmount) {
            state.addNewClaim(event.externalEventRequestId, selectedTokens)
            recordFactory.getSuccessfulClaimResponse(
                event.flowId,
                event.externalEventRequestId,
                event.poolKey,
                selectedTokens
            )
        } else {
            recordFactory.getFailedClaimResponse(event.flowId, event.externalEventRequestId, event.poolKey)
        }
    }
}
