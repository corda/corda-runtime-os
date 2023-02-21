@file:Suppress("UNUSED_PARAMETER")

package net.corda.v5.ledger.utxo.token.selection

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import java.math.BigDecimal

/**
 * This tests validates the code example in the KDoc comments will compile
 */
class TokenCacheFlowKotlinExample : ClientStartableFlow {

    @CordaInject
    lateinit var tokenCache: TokenSelection

    override fun call(requestBody: ClientRequestBody): String {
        // Create a criteria describing the tokens to be selected and
        // the target amount to be claimed.
        val criteria = TokenClaimCriteria(
            "Currency",
            getIssuerHash(),
            getNotaryX500Name(),
            "USD",
            BigDecimal(100)
        ).apply {
            // Set optional criteria
            ownerHash = getOwnerHash()
            tagRegex = "(test)"
        }

        // Call the token selection API to try and claim the tokens.
        val claim = tokenCache.tryClaim(criteria)

        if (claim == null) {
            // Not enough tokens could be claimed to satisfy the request.
            // take alternative action.
        } else {
            // Tokens we successfully claimed and can now be spent.
            val spentTokenRefs = spendTokens(claim.claimedTokens)

            // Release the claim by notifying the cache which tokens where spent. Any unspent tokens will be released
            // for other flows to claim.
            claim.useAndRelease(spentTokenRefs)
        }

        return "Done"
    }

    private fun getIssuerHash(): SecureHash{
        TODO()
    }

    private fun getOwnerHash(): SecureHash{
        TODO()
    }

    private fun getNotaryX500Name(): MemberX500Name{
        TODO()
    }

    private fun spendTokens(claimedTokens: List<ClaimedToken>): List<StateRef> {
        TODO()
    }
}
