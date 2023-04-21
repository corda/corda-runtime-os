package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.data.TokenClaim
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState

class PoolCacheStateImpl(private val cacheState: TokenPoolCacheState) : PoolCacheState {

    private var claimedTokens: Set<String>

    init {
        claimedTokens = createClaimedTokenSet()
    }

    override fun isTokenClaimed(stateRef: String): Boolean {
        return claimedTokens.contains(stateRef)
    }

    override fun claimExists(claimId: String): Boolean {
        return cacheState.tokenClaims.any { it.claimId == claimId }
    }

    override fun removeClaim(claimId: String) {
        cacheState.tokenClaims = cacheState.tokenClaims.filterNot { it.claimId == claimId }
        claimedTokens = createClaimedTokenSet()
    }

    override fun addNewClaim(claimId: String, selectedTokens: List<CachedToken>) {
        cacheState.tokenClaims = cacheState
            .tokenClaims
            .toMutableList().apply {
                removeIf { it.claimId == claimId }
                add(createClaim(claimId, selectedTokens))
            }
        claimedTokens = createClaimedTokenSet()
    }

    override fun tokensRemovedFromCache(stateRefs: Set<String>) {
        // When tokens are removed from the cache we also need to remove them from claims. If this leave the claim
        // empty then we should remove it as well.
        for (existingClaim in cacheState.tokenClaims) {
            existingClaim.claimedTokenStateRefs = existingClaim.claimedTokenStateRefs
                .filterNot { stateRefs.contains(it) }
        }

        cacheState.tokenClaims = cacheState.tokenClaims.filter { it.claimedTokenStateRefs.isNotEmpty() }
    }

    override fun toAvro(): TokenPoolCacheState {
        return cacheState
    }

    private fun createClaim(claimId: String, selectedTokens: List<CachedToken>): TokenClaim {
        return TokenClaim().apply {
            this.claimId = claimId
            this.claimedTokenStateRefs = selectedTokens.map { it.stateRef }
        }
    }

    private fun createClaimedTokenSet(): Set<String> {
        return cacheState.tokenClaims.flatMap { it.claimedTokenStateRefs }.toSet()
    }
}
