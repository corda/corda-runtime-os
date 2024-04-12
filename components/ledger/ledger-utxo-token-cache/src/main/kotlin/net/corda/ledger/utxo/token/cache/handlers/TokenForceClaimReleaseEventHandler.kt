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
        log.info("Force claim release received for claim id ${event.claimId}")
        log.debug { "Received token claim release for: $event" }

        if (!state.claimExists(event.claimId)) {
            log.warn("Couldn't find existing claim for claimId='${event.claimId}'")
        } else {
            log.info("Removing claims for ${event.claimId}")
            state.removeClaim(event.claimId)
        }

        return null
    }
}
