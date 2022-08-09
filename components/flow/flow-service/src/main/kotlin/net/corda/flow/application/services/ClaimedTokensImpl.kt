package net.corda.flow.application.services

import net.corda.flow.fiber.FlowFiberService
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.token.ClaimedTokensRelease
import net.corda.v5.application.services.ClaimedToken
import net.corda.v5.application.services.ClaimedTokens
import net.corda.v5.application.services.ClaimedTokensResultType
import net.corda.v5.base.annotations.Suspendable

class ClaimedTokensImpl(
    private val flowFiberService: FlowFiberService,
    override val claimId: String,
    claimedTokens: List<ClaimedToken>,
    override val resultType: ClaimedTokensResultType
) : ClaimedTokens {

    private val tokenIndex = claimedTokens.associateBy { it.stateRef }.toMutableMap()

    override val tokens: List<ClaimedToken>
        get() = tokenIndex.values.toList()

    @Suspendable
    override fun releaseAll() {
        guardMustHaveTokens()

        flowFiberService.getExecutingFiber().suspend(
            FlowIORequest.ClaimedTokenRelease(
                getClaimRelease(tokenIndex.keys.toList(), listOf())
            )
        )

        tokenIndex.clear()
    }

    @Suspendable
    override fun releaseOnly(tokenRefsToRelease: List<String>) {
        guardMustHaveTokens()

        flowFiberService.getExecutingFiber().suspend(
            FlowIORequest.ClaimedTokenRelease(
                getClaimRelease(tokenRefsToRelease, listOf())
            )
        )

        tokenRefsToRelease.forEach { tokenIndex.remove(it) }
    }

    @Suspendable
    override fun useAndRelease(usedTokensRefs: List<String>) {
        guardMustHaveTokens()

        flowFiberService.getExecutingFiber().suspend(
            FlowIORequest.ClaimedTokenRelease(
                getClaimRelease(getUnusedTokenRefs(usedTokensRefs), usedTokensRefs)
            )
        )

        usedTokensRefs.forEach { tokenIndex.remove(it) }
    }

    private fun guardMustHaveTokens() {
        if (tokenIndex.isEmpty()) {
            throw IllegalStateException("Can't release, no tokens available")
        }
    }

    private fun getUnusedTokenRefs(usedTokensRefs: List<String>): List<String> {
        val usedTokenRefSet = usedTokensRefs.toSet()
        return tokenIndex.keys.filterNot { usedTokenRefSet.contains(it) }.toList()
    }

    private fun getClaimRelease(tokensToRelease: List<String>, usedTokensRefs: List<String>): ClaimedTokensRelease {
        val token = tokenIndex.values.first()
        return ClaimedTokensRelease(
            claimId,
            token.tokenType,
            token.issuerHash,
            token.notaryHash,
            token.symbol,
            usedTokensRefs,
            tokensToRelease
        )
    }
}

