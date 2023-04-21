package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState

/**
 * The [PoolCacheState] represents the current state for a pool of cached [CachedToken]s
 */
interface PoolCacheState {

    /**
     * Checks the claimed status of a specific token
     *
     * @param stateRef The unique identifier of the token to check
     * @return True if the token is claimed
     */
    fun isTokenClaimed(stateRef: String): Boolean

    /**
     * Checks the existence of a specific claim
     *
     * @param claimId The unique identifier of the claim
     *
     * @return True of the claim exists.
     */
    fun claimExists(claimId: String): Boolean

    /**
     * Removes an existing claim from the cache if it exists
     *
     * @param claimId The unique identifier of the claim
     */
    fun removeClaim(claimId: String)

    /**
     * Adds a new claim to the cache
     *
     * @param claimId The unique identifier of the claim
     * @param selectedTokens This list of tokens to claim
     *
     * @throws IllegalStateException if the claim already exists
     */
    fun addNewClaim(claimId: String, selectedTokens: List<CachedToken>)

    /**
     * Called when tokens are removed from the [TokenCache]
     *
     * This method will remove the tokens from any existing claims. If this leaves the claim empty then the claim will
     * be deleted.
     *
     * @param stateRefs The list of token IDs to be removed.
     */
    fun tokensRemovedFromCache(stateRefs: Set<String>)

    /**
     * Creates an Avro representation of the [PoolCacheState].
     *
     * @return The Avro representation of the [PoolCacheState].
     */
    fun toAvro(): TokenPoolCacheState
}
