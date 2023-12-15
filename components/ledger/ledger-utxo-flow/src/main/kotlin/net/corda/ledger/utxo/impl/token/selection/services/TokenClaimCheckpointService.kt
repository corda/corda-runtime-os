package net.corda.ledger.utxo.impl.token.selection.services

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.flow.state.FlowCheckpoint
import net.corda.ledger.utxo.impl.token.selection.entities.TokenClaimCheckpointState

interface TokenClaimCheckpointService {

    fun addClaimToCheckpoint(checkpoint: FlowCheckpoint, claimId: String, poolKey: TokenPoolCacheKey)

    fun removeClaimFromCheckpoint(checkpoint: FlowCheckpoint, claimId: String)

    fun getTokenClaims(checkpoint: FlowCheckpoint): List<TokenClaimCheckpointState>
}
