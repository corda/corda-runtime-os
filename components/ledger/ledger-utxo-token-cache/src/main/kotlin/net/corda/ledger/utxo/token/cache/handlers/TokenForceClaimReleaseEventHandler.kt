package net.corda.ledger.utxo.token.cache.handlers

import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.token.cache.entities.ForceClaimRelease
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.messaging.api.records.Record
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

class TokenForceClaimReleaseEventHandler : TokenEventHandler<ForceClaimRelease> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun handle(
        tokenCache: TokenCache,
        state: PoolCacheState,
        event: ForceClaimRelease
    ): Record<String, FlowEvent>? {
        log.debug { "Received token claim release for: $event" }

        if (!state.claimExists(event.claimId)) {
            log.warn("Couldn't find existing claim for claimId='${event.claimId}'")
        } else {
            // The cache must be flushed because of the priority strategy
            // Otherwise the token priority won't be respected for tokens that have been released recently
            tokenCache.removeAll()
            state.removeClaim(event.claimId)
        }

        return null
    }
}
