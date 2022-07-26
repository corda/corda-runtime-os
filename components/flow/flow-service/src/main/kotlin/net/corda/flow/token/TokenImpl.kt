package net.corda.flow.token

import net.corda.v5.application.services.ClaimedTokens
import net.corda.v5.application.services.ClaimedToken

class ClaimedTokenImpl(
    override val stateRef: String,
    override val tokenType: String,
    override val issuerHash: String,
    override val notaryHash: String,
    override val symbol: String,
    override var tagRegex: String,
    override var ownerHash: String,
    override val amount: Long
) : ClaimedToken

class ClaimedTokensImpl(
    override val claimId: String,
    override val tokens: List<ClaimedToken>
    ) : ClaimedTokens {

    override fun releaseAll() {
        TODO("Not yet implemented")
    }

    override fun releaseOnly(tokensToRelease: List<String>) {
        TODO("Not yet implemented")
    }

    override fun useAndRelease(usedTokensRefs: List<String>) {
        TODO("Not yet implemented")
    }


}