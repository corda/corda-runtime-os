package net.corda.ledger.utxo.token.cache.entities.internal

import net.corda.data.ledger.utxo.token.selection.data.TokenClaim
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.converters.EntityConverterImpl
import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState

class PoolCacheStateImpl(private val cacheState: TokenPoolCacheState) : PoolCacheState {

    private var claimedTokens: Map<String, CachedToken>

    init {
        claimedTokens = createClaimedTokenMap()
    }

    override fun isTokenClaimed(stateRef: String): Boolean {
        return claimedTokens.contains(stateRef)
    }

    override fun claimExists(claimId: String): Boolean {
        return cacheState.tokenClaims.any { it.claimId == claimId }
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

    override fun toAvro(): TokenPoolCacheState {
        return cacheState
    }

    private fun createClaim(claimId: String, selectedTokens: List<CachedToken>): TokenClaim {
        return TokenClaim().apply {
            this.claimId = claimId
            this.claimedTokens = selectedTokens.map { it.toAvro() }
        }
    }

    private fun createClaimedTokenMap(): Map<String, CachedToken> {
        return cacheState.tokenClaims
            .flatMap { tokenClaim -> tokenClaim.claimedTokens }
            .associateBy ( { it.stateRef }, { CachedTokenImpl( it, EntityConverterImpl()) } )
    }
}
