package net.corda.ledger.utxo.impl.token.selection.services

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.impl.token.selection.entities.TokenClaimCheckpointState
import net.corda.ledger.utxo.impl.token.selection.entities.TokenClaimsCheckpointState
import net.corda.ledger.utxo.impl.token.selection.factories.TokenClaimFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [TokenClaimCheckpointService::class])
class TokenClaimCheckpointServiceImpl @Activate constructor(
    @Reference(service = TokenClaimFactory::class)
    private val tokenClaimFactory: TokenClaimFactory
) : TokenClaimCheckpointService {

    override fun addClaimToCheckpoint(checkpoint: FlowCheckpoint, claimId: String, poolKey: TokenPoolCacheKey) {
        val checkpointState = checkpoint.readCustomState(TokenClaimsCheckpointState::class.java)
            ?: TokenClaimsCheckpointState(mutableListOf())

        checkpointState.claims.add(
            tokenClaimFactory.createTokenClaimCheckpointState(claimId, poolKey)
        )

        checkpoint.writeCustomState(checkpointState)
    }

    override fun removeClaimFromCheckpoint(checkpoint: FlowCheckpoint, claimId: String) {
        val checkpointState = checkpoint.readCustomState(TokenClaimsCheckpointState::class.java)
            ?: TokenClaimsCheckpointState(mutableListOf())

        checkpointState.claims.removeIf { it.claimId == claimId }

        checkpoint.writeCustomState(checkpointState)
    }

    override fun getTokenClaims(checkpoint: FlowCheckpoint): List<TokenClaimCheckpointState> {
        return (
            checkpoint.readCustomState(TokenClaimsCheckpointState::class.java)
                ?: TokenClaimsCheckpointState(mutableListOf())
            ).claims
    }
}
