package net.corda.v5.ledger.utxo.token.selection

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.StateRef

/**
 * [TokenClaim] is a claimed set of tokens returned by a call to [TokenSelection.tryClaim].
 *
 * The claimed [ClaimedToken] list is exclusively locked by the flow that made the claim and are unavailable to any
 * other flows. Once a flow has either spent some or all of the claimed tokens it should call [TokenClaim.useAndRelease]
 * to notify the cache which tokens were used. Any unused tokens will be released and made available to other flows.
 * If the flow does not call [TokenClaim.useAndRelease] the tokens will remain locked until the cache receives a
 *
 * consumed notification from the vault or the claim timeout elapses.
 *
 * @property claimedTokens List of [ClaimedToken] claimed.
 */
@DoNotImplement
interface TokenClaim {

    val claimedTokens: List<ClaimedToken>

    /**
     * Removes any used tokens from the cache and unlocks any remaining tokens for other flows to claim.
     *
     * @param usedTokensRefs List of state refs to mark as used.
     */
    @Suspendable
    fun useAndRelease(usedTokensRefs: List<StateRef>)
}
