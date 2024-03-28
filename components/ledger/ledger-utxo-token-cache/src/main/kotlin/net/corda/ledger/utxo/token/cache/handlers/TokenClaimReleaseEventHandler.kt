package net.corda.ledger.utxo.token.cache.handlers

import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.token.cache.entities.ClaimRelease
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.factories.RecordFactory
import net.corda.messaging.api.records.Record
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

class TokenClaimReleaseEventHandler(
    private val recordFactory: RecordFactory,
) : TokenEventHandler<ClaimRelease> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun handle(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: ClaimRelease
    ): Record<String, FlowEvent>? {
        log.debug { "Received token claim release for: $event" }

        if (!state.claimExists(event.claimId)) {
            log.warn("Couldn't find existing claim for claimId='${event.claimId}'")
        } else {
            tokenCache.removeAll(event.usedTokens)
            state.removeClaim(event.claimId)
        }

        return recordFactory.getClaimReleaseAck(event.flowId, event.externalEventRequestId)
    }
}
