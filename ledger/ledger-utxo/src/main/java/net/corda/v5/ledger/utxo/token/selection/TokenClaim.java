package net.corda.v5.ledger.utxo.token.selection;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.ledger.utxo.StateRef;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Defines a claimed set of tokens returned by a call to {@link TokenSelection#tryClaim(TokenClaimCriteria)}.
 * <p>
 * The claimed {@link ClaimedToken} list is exclusively locked by the flow that made the claim and are
 * unavailable to any other flows.
 * <p>
 * Once a flow has either spent some or all of the claimed tokens it should call {@link TokenClaim#useAndRelease(List)}
 * to notify the cache which tokens were used.
 * <p>
 * Any unused tokens will be released and made available to other flows.
 * <p>
 * If the flow does not call {@link TokenClaim#useAndRelease(List)} the tokens will remain locked until the cache
 * receives a consumed notification from the vault or the claim timeout elapses.
 */
@DoNotImplement
public interface TokenClaim {

    /**
     * Gets a list of claimed tokens.
     *
     * @return Returns a list of claimed tokens.
     */
    @NotNull
    List<ClaimedToken> getClaimedTokens();

    /**
     * Removes any used tokens from the cache and unlocks any remaining tokens for other flows to claim.
     *
     * @param usedTokensRefs The {@link List} of {@link StateRef}s to mark as used.
     */
    @Suspendable
    void useAndRelease(@NotNull List<StateRef> usedTokensRefs);
}
