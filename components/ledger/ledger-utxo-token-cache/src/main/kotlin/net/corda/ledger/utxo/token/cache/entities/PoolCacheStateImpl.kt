package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.data.TokenClaim
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState

class PoolCacheStateImpl() : PoolCacheState {

    private val cacheState = TokenPoolCacheState()
    private val claimedTokensMap: MutableMap<String, CachedToken> = mutableMapOf()


    override fun claimedTokens(): Collection<CachedToken> {
        return claimedTokensMap.values
    }

    override fun isTokenClaimed(stateRef: String): Boolean {
        return claimedTokensMap.contains(stateRef)
    }

    override fun claimExists(claimId: String): Boolean {
        return cacheState.tokenClaims.any { it.claimId == claimId }
    }

    override fun removeClaim(claimId: String) {
        cacheState.tokenClaims = cacheState.tokenClaims.filterNot { tokenClaim ->
            if(tokenClaim.claimId == claimId) {
                removeFromClaimedTokenMap(tokenClaim.claimedTokenStateRefs)
                true
            }
            else {
                false
            }
        }
    }

    override fun addNewClaim(claimId: String, selectedTokens: List<CachedToken>) {
        cacheState.tokenClaims = cacheState
            .tokenClaims
            .toMutableList().apply {
                removeIf {
                    tokenClaim ->
                    if(tokenClaim.claimId == claimId) {
                        removeFromClaimedTokenMap(tokenClaim.claimedTokenStateRefs)
                        true
                    }
                    else {
                        false
                    }
                }
                add(createClaim(claimId, selectedTokens))
            }

        addToClaimedTokenMap(selectedTokens)
    }

    override fun tokensRemovedFromCache(stateRefs: Set<String>) {
        // When tokens are removed from the cache we also need to remove them from claims. If this leave the claim
        // empty then we should remove it as well.
        for (existingClaim in cacheState.tokenClaims) {
            existingClaim.claimedTokenStateRefs = existingClaim.claimedTokenStateRefs
                .filterNot { stateRefs.contains(it) }
        }

        cacheState.tokenClaims = cacheState.tokenClaims.filter { it.claimedTokenStateRefs.isNotEmpty() }
        removeFromClaimedTokenMap(stateRefs)
    }

    override fun toAvro(): TokenPoolCacheState {
        return cacheState
    }

    private fun createClaim(claimId: String, selectedTokens: Collection<CachedToken>): TokenClaim {
        return TokenClaim().apply {
            this.claimId = claimId
            this.claimedTokenStateRefs = selectedTokens.map { it.stateRef }
        }
    }

    private fun addToClaimedTokenMap(selectedTokens: Collection<CachedToken>) {
        selectedTokens.forEach { claimedTokensMap[it.stateRef] = it }
    }

    private fun removeFromClaimedTokenMap(claimedTokenStateRefs: Collection<String>) {
        claimedTokenStateRefs.forEach { stateRef ->
            claimedTokensMap.remove(stateRef)
        }
    }
}
