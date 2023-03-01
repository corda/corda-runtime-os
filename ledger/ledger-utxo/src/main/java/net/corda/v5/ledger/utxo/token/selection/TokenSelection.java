package net.corda.v5.ledger.utxo.token.selection;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines a mechanism to allow flows to query the token cache and claim a list of {@link ClaimedToken} to spend.
 * <p>
 * The API allows a flow to query for a target amount of a given state/token type it wishes to spend.
 * If available a set of tokens will be selected that sum to at least the target amount specified.
 * The tokens will be locked in the cache to prevent other flows from selecting them.
 * <p>
 * The platform will provide an instance of {@link TokenSelection} to flows via property injection.
 * <p>
 * Example of use in Kotlin.
 * ```Kotlin
 *
 * @CordaInject lateinit var tokenSelection: TokenSelection
 * <p>
 * override fun call(requestBody: RestRequestBody): String {
 * // Create a criteria describing the tokens to be selected and
 * // the target amount to be claimed.
 * val criteria = TokenClaimCriteria(
 * "Currency",
 * getIssuerHash(),
 * getNotaryX500Name(),
 * "USD",
 * BigDecimal(100)
 * )
 * <p>
 * // Call the token selection API to try and claim the tokens.
 * val claim = tokenSelection.tryClaim(criteria)
 * <p>
 * if (claim == null) {
 * // Not enough tokens could be claimed to satisfy the request.
 * // take alternative action.
 * } else {
 * // Tokens we successfully claimed and can now be spent.
 * val spentTokenRefs = spendTokens(claim.claimedTokens)
 * <p>
 * // Release the claim by notifying the cache which tokens where spent. Any unspent tokens will be released
 * // for other flows to claim.
 * claim.useAndRelease(spentTokenRefs!!)
 * }
 * <p>
 * return "Done"
 * }
 * ```
 * <p>
 * Example of use in Java.
 * ```Java
 * @CordaInject public TokenSelection tokenSelection;
 * @Override public String call(RestRequestBody requestBody) {
 * <p>
 * // Create a criteria describing the tokens to be selected and
 * // the target amount to be claimed.
 * TokenClaimCriteria criteria = new TokenClaimCriteria (
 * "Currency",
 * getIssuerHash(),
 * getNotaryX500Name(),
 * "USD",
 * new BigDecimal(100)
 * );
 * <p>
 * // Call the token selection API to try and claim the tokens.
 * TokenClaim claim = tokenSelection.tryClaim(criteria);
 * <p>
 * if (claim == null) {
 * // Not enough tokens could be claimed to satisfy the request.
 * // take alternative action.
 * } else {
 * // Tokens we successfully claimed and can now be spent.
 * List<StateRef> spentTokenRefs = spendTokens(claim.getClaimedTokens());
 * <p>
 * // Release the claim by notifying the cache which tokens where spent. Any unspent tokens will be released
 * // for other flows to claim.
 * claim.useAndRelease(spentTokenRefs);
 * }
 * <p>
 * return "Done";
 * }
 * ```
 */
@DoNotImplement
public interface TokenSelection {

    /**
     * Attempts to claim a set of tokens from the cache that satisfies the specified {@link TokenClaimCriteria}.
     *
     * @param criteria The {@link TokenClaimCriteria} used to select tokens.
     * @return Returns a {@link TokenClaim} if enough tokens were claimed to satisfy the {@link TokenClaimCriteria#getTargetAmount()},
     * or null If the {@link TokenClaimCriteria#getTargetAmount()} could not be reached.
     */
    @Nullable
    @Suspendable
    TokenClaim tryClaim(@NotNull TokenClaimCriteria criteria);
}
