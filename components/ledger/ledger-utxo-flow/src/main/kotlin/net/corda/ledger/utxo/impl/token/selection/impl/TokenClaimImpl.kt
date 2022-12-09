package net.corda.ledger.utxo.impl.token.selection.impl

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.utxo.impl.token.selection.factories.ClaimReleaseExternalEventFactory
import net.corda.ledger.utxo.impl.token.selection.factories.ClaimReleaseParameters
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.token.selection.ClaimedToken
import net.corda.v5.ledger.utxo.token.selection.TokenClaim

class TokenClaimImpl(
    private val claimId: String,
    private val poolKey: PoolKey,
    override val claimedTokens: List<ClaimedToken>,
    private val externalEventExecutor: ExternalEventExecutor
) : TokenClaim {

    @Suspendable
    override fun useAndRelease(usedTokensRefs: List<StateRef>) {
        externalEventExecutor.execute(
            ClaimReleaseExternalEventFactory::class.java,
            ClaimReleaseParameters(claimId, poolKey, usedTokensRefs)
        )
    }
}
