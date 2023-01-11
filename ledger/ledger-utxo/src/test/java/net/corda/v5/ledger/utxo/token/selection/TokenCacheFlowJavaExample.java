package net.corda.v5.ledger.utxo.token.selection;

import net.corda.v5.application.flows.CordaInject;
import net.corda.v5.application.flows.RestRequestBody;
import net.corda.v5.application.flows.RestStartableFlow;
import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.utxo.StateRef;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * This tests validates the code example in the KDoc comments will compile
 */
public class TokenCacheFlowJavaExample implements RestStartableFlow {
    @CordaInject
    public TokenSelection tokenCache;

    @Override
    @NotNull
    public String call(@NotNull RestRequestBody requestBody) {

        // Create a criteria describing the tokens to be selected and
        // the target amount to be claimed.
        TokenClaimCriteria criteria = new TokenClaimCriteria(
                "Currency",
                getIssuerHash(),
                getNotaryX500Name(),
                "USD",
                new BigDecimal(100)
        );

        // Set optional criteria
        criteria.setOwnerHash(getOwnerHash());
        criteria.setTagRegex("(test)");

        // Call the token selection API to try and claim the tokens.
        TokenClaim claim = tokenCache.tryClaim(criteria);

        if (claim == null) {
            // Not enough tokens could be claimed to satisfy the request.
            // take alternative action.
        } else {
            // Tokens we successfully claimed and can now be spent.
            List<StateRef> spentTokenRefs = spendTokens(claim.getClaimedTokens());

            // Release the claim by notifying the cache which tokens where spent. Any unspent tokens will be released
            // for other flows to claim.
            claim.useAndRelease(spentTokenRefs);
        }

        return "Done";
    }

    @NotNull
    private SecureHash getIssuerHash() {
        return null;
    }

    @NotNull
    private SecureHash getOwnerHash() {
        return null;
    }

    @NotNull
    private MemberX500Name getNotaryX500Name() {
        return null;
    }

    @NotNull
    private List<StateRef> spendTokens(List<ClaimedToken> claimedTokens) {
        return null;
    }
}