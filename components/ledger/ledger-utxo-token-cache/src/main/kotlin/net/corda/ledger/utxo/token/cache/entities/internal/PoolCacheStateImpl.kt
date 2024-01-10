package net.corda.ledger.utxo.token.cache.entities.internal

import net.corda.data.ledger.utxo.token.selection.data.TokenClaim
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.services.ServiceConfiguration
import net.corda.utilities.time.Clock
import org.slf4j.LoggerFactory
import java.time.Duration

class PoolCacheStateImpl(
    private val cacheState: TokenPoolCacheState,
    serviceConfiguration: ServiceConfiguration,
    private val entityConverter: EntityConverter,
    private val clock: Clock
) : PoolCacheState {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private var claimedTokens: Map<String, CachedToken>
    private val claimTimeoutOffsetMillis = Duration
        .ofSeconds(serviceConfiguration.claimTimeoutSeconds.toLong())
        .toMillis()

    init {
        claimedTokens = createClaimedTokenMap()
    }

    override fun isTokenClaimed(stateRef: String): Boolean {
        return claimedTokens.contains(stateRef)
    }

    override fun claimExists(claimId: String): Boolean {
        return cacheState.tokenClaims.any { it.claimId == claimId }
    }

    override fun claim(claimId: String): TokenClaim? {
        // There must only exist one claim
        return cacheState.tokenClaims.filter { it.claimId == claimId }.singleOrNull()
    }

    override fun removeClaim(claimId: String) {
        cacheState.tokenClaims = cacheState.tokenClaims.filterNot { it.claimId == claimId }
        claimedTokens = createClaimedTokenMap()
    }

    override fun addNewClaim(claimId: String, selectedTokens: List<CachedToken>) {
        cacheState.tokenClaims = cacheState
            .tokenClaims
            .toMutableList().apply {
                removeIf { it.claimId == claimId }
                add(createClaim(claimId, selectedTokens))
            }
        claimedTokens = createClaimedTokenMap()
    }

    override fun tokensRemovedFromCache(stateRefs: Set<String>) {
        // When tokens are removed from the cache we also need to remove them from claims. If this leave the claim
        // empty then we should remove it as well.
        for (existingClaim in cacheState.tokenClaims) {
            existingClaim.claimedTokens = existingClaim.claimedTokens
                .filterNot { stateRefs.contains(it.stateRef) }
        }

        cacheState.tokenClaims = cacheState.tokenClaims.filter { it.claimedTokens.isNotEmpty() }
    }

    override fun claimedTokens(): Collection<CachedToken> {
        return claimedTokens.values
    }

    override fun removeExpiredClaims() {
        val now = clock.instant().toEpochMilli()

        val claimsToRemove = cacheState.tokenClaims
            .filter { it.claimTimestamp != null && (it.claimTimestamp + claimTimeoutOffsetMillis) < now }
            .map { it.claimId }
            .toSet()

        if (claimsToRemove.isNotEmpty()) {
            cacheState.tokenClaims = cacheState.tokenClaims.filterNot { claimsToRemove.contains(it.claimId) }
            claimedTokens = createClaimedTokenMap()
        }

        // To handle upgrade, claims with a null timestamp are assumed to be from a previous version of the code
        // and should be set with the current timestamp
        for (tokenClaim in cacheState.tokenClaims) {
            if (tokenClaim.claimTimestamp == null) {
                tokenClaim.claimTimestamp = now
            }
        }
    }

    override fun removeInvalidClaims() {
        // Temporary logic that covers the upgrade from release/5.0 to release/5.1
        // The field claimedTokens has been added to the TokenClaim avro object, and it will replace claimedTokenStateRefs.
        // In order to avoid breaking compatibility, the claimedTokenStateRefs has been deprecated, and it will eventually
        // be removed. Any claim that contains a non-empty claimedTokenStateRefs field are considered invalid because
        // this means the avro object is an old one, and it should be replaced by the new format.
        val invalidClaims =
            cacheState.tokenClaims.filterNot { it.claimedTokenStateRefs.isNullOrEmpty() }
        if (invalidClaims.isNotEmpty()) {
            val invalidClaimsId = invalidClaims.map {
                removeClaim(it.claimId)
                it.claimId
            }
            logger.warn("Invalid claims were found and have been discarded. Invalid claims: $invalidClaimsId")
        }
    }

    override fun toAvro(): TokenPoolCacheState {
        return cacheState
    }

    private fun createClaim(claimId: String, selectedTokens: List<CachedToken>): TokenClaim {
        return TokenClaim.newBuilder()
            .setClaimId(claimId)
            .setClaimedTokens(selectedTokens.map { it.toAvro() })
            .build()
    }

    private fun createClaimedTokenMap(): Map<String, CachedToken> {
        return cacheState.tokenClaims
            .flatMap { tokenClaim -> tokenClaim.claimedTokens }
            .associateBy({ it.stateRef }, { entityConverter.toCachedToken(it) })
    }
}
