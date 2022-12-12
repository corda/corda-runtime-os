package net.corda.ledger.utxo.impl.token.selection.factories

import net.corda.data.ledger.utxo.token.selection.data.Token
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.v5.ledger.utxo.token.selection.ClaimedToken
import net.corda.v5.ledger.utxo.token.selection.TokenClaim

/**
 * The [TokenClaimFactory] creates instances for a [TokenClaim]
 */
interface TokenClaimFactory {

    /**
     * Creates an instance of [TokenClaim]
     *
     * @param claimId The unique identifier for the claim
     * @param poolKey The unique key for the pool of tokens
     * @param claimedTokens The list of tokens in this claim.
     *
     * @return A new instance of [TokenClaim]
     */
    fun createTokenClaim(claimId: String, poolKey: TokenPoolCacheKey, claimedTokens: List<ClaimedToken>): TokenClaim

    /**
     * Creates an instance of [ClaimedToken]
     *
     * @param poolKey The unique key for the pool the token belongs to
     * @param token The avro representation of the token
     */
    fun createClaimedToken(poolKey: TokenPoolCacheKey, token: Token): ClaimedToken
}
